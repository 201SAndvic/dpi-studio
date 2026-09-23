package com.appconfig.injector.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.appconfig.injector.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File as JavaFile

/** 文件浏览器的用途。 */
enum class PickerMode {
    /** 只列文件夹和 .apk */
    Apk,

    /** 列文件夹和所有文件 */
    AnyFile,

    /** 只列文件夹，用来选保存位置 */
    Folder,
}

/**
 * 应用内置的文件浏览器。
 *
 * 车机上系统自带的 DocumentsUI 经常被裁掉，用 ACTION_OPEN_DOCUMENT 会直接崩，
 * 所以这里自己列目录，默认打开下载文件夹。
 */
@Composable
fun FilePickerScreen(
    mode: PickerMode,
    onPickFile: (JavaFile) -> Unit,
    onPickFolder: (JavaFile) -> Unit,
    onCancel: () -> Unit,
    onUseSystemPicker: () -> Unit,
) {
    val ctx = LocalContext.current

    var accessKey by remember { mutableIntStateOf(0) }
    val granted = remember(accessKey) { hasStorageAccess(ctx) }
    var awaitingGrant by remember { mutableStateOf(false) }

    // 从系统设置页回来后重新检查权限
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessKey++
                // 刚授完权就重启：车机上加权限时应用还活着，存储挂载还是旧的受限视图，
                // 会出现「能列目录但打不开文件」。重启进程才会拿到完整视图。
                if (awaitingGrant && hasStorageAccess(ctx)) {
                    awaitingGrant = false
                    Messages.show(ctx.getString(R.string.picker_restart))
                    restartApp(ctx)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { accessKey++ }

    var current by remember { mutableStateOf(downloadsDir()) }
    var entries by remember { mutableStateOf<List<JavaFile>>(emptyList()) }
    var unreadableSet by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var unreadable by remember { mutableStateOf(false) }

    LaunchedEffect(current, accessKey) {
        if (!granted) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val result = withContext(Dispatchers.IO) {
            listDir(current) { f ->
                when (mode) {
                    PickerMode.Apk -> f.name.endsWith(".apk", true)
                    PickerMode.AnyFile -> true
                    PickerMode.Folder -> false
                }
            }
        }
        unreadable = result == null
        entries = result.orEmpty()
        // 选 APK 时提前探一遍可读性：存储层拒绝访问的文件，选择时才发现就太晚了
        unreadableSet = if (mode == PickerMode.Apk) {
            withContext(Dispatchers.IO) {
                entries.filter { !it.isDirectory && !canOpenForRead(it) }
                    .map { it.absolutePath }
                    .toSet()
            }
        } else {
            emptySet()
        }
        loading = false
    }

    val title = when (mode) {
        PickerMode.Apk -> ctx.getString(R.string.picker_title)
        PickerMode.AnyFile -> ctx.getString(R.string.picker_file_title)
        PickerMode.Folder -> ctx.getString(R.string.picker_folder_title)
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                subtitle = current.absolutePath,
                navigationIcon = {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = ctx.getString(R.string.cancel),
                            modifier = Modifier
                                .size(width = 11.dp, height = 17.dp)
                                .rotate(180f),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
        containerColor = MiuixTheme.colorScheme.surface,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            // 快捷跳转
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = ctx.getString(R.string.picker_up),
                    onClick = { current.parentFile?.let { current = it } },
                    modifier = Modifier.weight(1f),
                    minHeight = 40.dp,
                    insideMargin = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                )
                TextButton(
                    text = ctx.getString(R.string.picker_downloads),
                    onClick = { current = downloadsDir() },
                    modifier = Modifier.weight(1f),
                    minHeight = 40.dp,
                    insideMargin = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                )
                TextButton(
                    text = ctx.getString(R.string.picker_app_dir),
                    onClick = {
                        current = appFilesDir(ctx).also { if (!it.exists()) it.mkdirs() }
                    },
                    modifier = Modifier.weight(1f),
                    minHeight = 40.dp,
                    insideMargin = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                )
                TextButton(
                    text = ctx.getString(R.string.picker_root),
                    onClick = { current = storageRoot() },
                    modifier = Modifier.weight(1f),
                    minHeight = 40.dp,
                    insideMargin = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                )
            }

            when {
                !granted -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = ctx.getString(R.string.picker_denied),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                awaitingGrant = true
                                openStorageAccessSettings(ctx)
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(
                            text = ctx.getString(R.string.picker_grant),
                            style = MiuixTheme.textStyles.button,
                            color = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }

                loading -> Hint(ctx.getString(R.string.picker_loading))
                unreadable -> Hint(ctx.getString(R.string.picker_denied))
                entries.isEmpty() -> Hint(
                    if (mode == PickerMode.Folder) {
                        ctx.getString(R.string.picker_folder_empty)
                    } else {
                        ctx.getString(R.string.picker_empty)
                    }
                )

                else -> AppCard(modifier = Modifier.weight(1f)) {
                    LazyColumn {
                        items(entries, key = { it.absolutePath }) { f ->
                            FileRow(
                                file = f,
                                readable = !unreadableSet.contains(f.absolutePath),
                                onClick = {
                                    if (f.isDirectory) {
                                        current = f
                                    } else {
                                        onPickFile(f)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (mode == PickerMode.Folder) {
                    // 选文件夹：把当前目录作为保存位置
                    Button(
                        onClick = {
                            current.also { if (!it.exists()) it.mkdirs() }
                            onPickFolder(current)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(
                            text = ctx.getString(R.string.picker_use_folder),
                            style = MiuixTheme.textStyles.button,
                            color = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                    Text(
                        text = current.absolutePath,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                } else {
                    TextButton(
                        text = ctx.getString(R.string.picker_system),
                        onClick = onUseSystemPicker,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = ctx.getString(
                        if (granted) R.string.picker_access_ok else R.string.picker_access_no
                    ),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
    )
}

@Composable
private fun FileRow(file: JavaFile, readable: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (file.isDirectory) MiuixIcons.Medium.Folder else MiuixIcons.Medium.File,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.SemiBold,
            )
            if (!file.isDirectory) {
                Text(
                    text = if (readable) {
                        formatSize(file.length())
                    } else {
                        formatSize(file.length()) + " · " +
                            ctx.getString(R.string.picker_unreadable)
                    },
                    style = MiuixTheme.textStyles.body2,
                    color = if (readable) {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    } else {
                        MiuixTheme.colorScheme.error
                    },
                )
            }
        }
        if (file.isDirectory) {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(width = 9.dp, height = 15.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/** 重启应用：特殊权限（所有文件访问）在运行中授予时，需要重建进程才能生效。 */
private fun restartApp(ctx: Context) {
    val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    ctx.startActivity(intent)
    (ctx as? Activity)?.finishAffinity()
    Handler(Looper.getMainLooper()).postDelayed({
        Process.killProcess(Process.myPid())
    }, 600)
}
