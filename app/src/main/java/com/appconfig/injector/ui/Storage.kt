package com.appconfig.injector.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File

/** 是否已经拿到「读取整块存储」的权限。 */
fun hasStorageAccess(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

/** Android 11+ 去申请「所有文件访问」；更早的版本走运行时权限。 */
fun openStorageAccessSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val app = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.fromParts("package", ctx.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(app)
        } catch (_: Exception) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                // 车机可能把这个设置页也裁掉了，交给上面的提示文案兜底
            }
        }
    }
}

/** 下载目录：不同车机叫法不一样，挨个试。 */
fun downloadsDir(): File {
    val candidates = listOf(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        File(Environment.getExternalStorageDirectory(), "Download"),
        File(Environment.getExternalStorageDirectory(), "Downloads"),
    )
    return candidates.firstOrNull { it.isDirectory } ?: storageRoot()
}

fun storageRoot(): File = Environment.getExternalStorageDirectory()

/**
 * 应用自己的外部目录：`/sdcard/Android/data/<包名>/files`。
 * 系统一定允许读写，权限受限时可以把 APK 放这里再选。
 */
fun appFilesDir(ctx: Context): File = ctx.getExternalFilesDir(null) ?: ctx.filesDir

/** 列出目录内容（按 filter 过滤文件，文件夹始终保留）。无权读取时返回 null。 */
fun listDir(dir: File, filter: (File) -> Boolean): List<File>? {
    val files = dir.listFiles() ?: return null
    return files
        .filter { !it.name.startsWith(".") }
        .filter { it.isDirectory || filter(it) }
        .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
}

/** 把文件复制进指定目录，重名时自动加序号；成功返回目标文件。 */
fun copyIntoDir(source: File, dir: File): File? {
    if (!dir.isDirectory && !dir.mkdirs()) return null
    val name = source.name
    val base = name.substringBeforeLast('.', name)
    val ext = name.substringAfterLast('.', "")
    var target = File(dir, name)
    var index = 1
    while (target.exists()) {
        target = File(dir, if (ext.isEmpty()) "$base ($index)" else "$base ($index).$ext")
        index++
    }
    return try {
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
        }
        target
    } catch (_: Throwable) {
        null
    }
}

/** 直接写进系统下载目录（不弹系统选择器，车机上才不会崩）。成功返回文件名。 */
fun saveBytesToDownloads(ctx: Context, name: String, bytes: ByteArray, mime: String): String? {
    val tmp = File(ctx.cacheDir, name)
    return try {
        tmp.outputStream().use { it.write(bytes) }
        saveFileToDownloads(ctx, name, tmp, mime)
    } catch (_: Throwable) {
        null
    } finally {
        tmp.delete()
    }
}

fun saveFileToDownloads(ctx: Context, name: String, source: File, mime: String): String? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            null
        } else {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output, 1 shl 16) }
            }
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            name
        }
    } else {
        copyIntoDir(source, downloadsDir())?.absolutePath
    }
} catch (_: Throwable) {
    null
}

/**
 * 这个文件现在能不能被本应用打开读取。
 *
 * 用于提前标出「系统存储层拒绝访问」的文件：
 * 目录能列出来但打开报 EACCES 的，多半是副本写入没收尾（属主/状态异常），
 * 这类文件其它应用同样读不到。
 */
fun canOpenForRead(file: File): Boolean = try {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).close()
    true
} catch (_: Throwable) {
    false
}
