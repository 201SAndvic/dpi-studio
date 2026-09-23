package com.appconfig.injector.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Display
import android.view.PixelCopy
import android.view.WindowManager
import com.appconfig.injector.BuildConfig
import com.appconfig.injector.Prefs
import com.appconfig.injector.Logs
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * 「保存当前环境」：把屏幕数量 / 分辨率 / dpi / 当前所在屏幕 / 截图 / 参数打包成 zip，
 * 直接写进下载目录。
 */
object EnvSnapshot {

    suspend fun build(ctx: Context, activity: Activity, state: AppState): ByteArray {
        val text = report(ctx, activity, state)
        val shot = captureWindow(activity)
        val config = runCatching { Prefs.exportJson(ctx) }.getOrElse { "{}" }

        return withContext(Dispatchers.IO) {
            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("环境信息.txt"))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("配置.json"))
                zip.write(config.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                val log = reportHeader(ctx, appVersionName(ctx)) + Logs.text()
                if (log.isNotBlank()) {
                    zip.putNextEntry(ZipEntry("日志.txt"))
                    zip.write(log.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }

                if (shot != null) {
                    val png = ByteArrayOutputStream()
                    shot.compress(Bitmap.CompressFormat.PNG, 100, png)
                    val bytes = png.toByteArray()
                    // 压缩失败会写出 0 字节的图，那在解压端就是「损坏」的，不如不放
                    if (bytes.isNotEmpty()) {
                        zip.putNextEntry(ZipEntry("截图.png"))
                        zip.write(bytes)
                        zip.closeEntry()
                    }
                    shot.recycle()
                }
            }
            bos.toByteArray()
        }
    }

    fun fileName(): String =
        "DPI工坊-环境-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".zip"

    private fun report(ctx: Context, activity: Activity, state: AppState): String = buildString {
        val dm = ctx.resources.displayMetrics
        val currentDp = currentScreenDp(ctx)
        val self = state.selfMinWidth

        appendLine("==== DPI 工坊 环境报告 ====")
        appendLine("时间：" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        appendLine("应用版本：" + BuildConfig.VERSION_NAME)
        appendLine(
            "本机显示尺寸：" +
                if (self > 0) "$self dp（手动设置）" else "跟随系统（未调整）"
        )
        appendLine()

        appendLine("-- 设备 --")
        appendLine("系统版本：" + Build.VERSION.RELEASE)
        appendLine("API 级别：" + Build.VERSION.SDK_INT)
        appendLine("设备型号：" + Build.MANUFACTURER + " " + Build.MODEL)
        appendLine("品牌：" + Build.BRAND)
        appendLine("处理器架构：" + (Build.SUPPORTED_ABIS.firstOrNull() ?: "-"))
        appendLine()

        val displays = displayList(ctx)
        appendLine("-- 屏幕（共 ${displays.size} 块）--")
        displays.forEachIndexed { index, display ->
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            runCatching { display.getRealMetrics(m) }.onFailure { display.getMetrics(m) }
            val dpWidth = if (m.density > 0) (m.widthPixels / m.density).toInt() else 0
            val dpHeight = if (m.density > 0) (m.heightPixels / m.density).toInt() else 0
            appendLine("[${titleOf(ctx, display, index)}] displayId=${display.displayId}  名称=${display.name}")
            appendLine("  分辨率：${m.widthPixels} × ${m.heightPixels}")
            appendLine("  密度：${m.densityDpi} dpi（density=${m.density}）")
            appendLine("  按上报 dpi 换算：${dpWidth} × ${dpHeight} dp")
            appendLine("  物理尺寸：" + inches(m))
            if (self > 0) {
                val h = Math.round(self.toFloat() * m.heightPixels / m.widthPixels)
                appendLine("  设为本机尺寸后：$self × $h dp")
            }
        }
        appendLine()

        val currentId = currentDisplayId(activity)
        val currentIndex = displays.indexOfFirst { it.displayId == currentId }
        appendLine("-- 当前应用所在屏幕 --")
        val label = if (currentIndex >= 0) titleOf(ctx, displays[currentIndex], currentIndex) else "未知"
        appendLine("displayId=$currentId（$label）")
        appendLine("当前屏幕 dp 宽：$currentDp dp")
        appendLine()

        appendLine("-- 当前注入参数 --")
        appendLine(state.config.summary())
        appendLine(state.config.toModuleConfig().toXml())
    }

    // ---------------------------------------------------------------- 屏幕信息

    private fun displayList(ctx: Context): List<Display> {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return emptyList()
        return dm.displays.filter { it.isValid && !it.name.orEmpty().startsWith("Overlay") }
    }

    private fun titleOf(ctx: Context, d: Display, index: Int): String = when {
        d.displayId == Display.DEFAULT_DISPLAY -> "主屏"
        index == 1 -> "副屏"
        else -> "屏幕 ${index + 1}"
    }

    private fun currentDisplayId(activity: Activity): Int? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.displayId
        } else {
            @Suppress("DEPRECATION")
            (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.displayId
        }
    } catch (_: Throwable) {
        null
    }

    private fun inches(m: android.util.DisplayMetrics): String = try {
        if (m.xdpi <= 0 || m.ydpi <= 0) "-"
        else {
            val w = m.widthPixels / m.xdpi.toDouble()
            val h = m.heightPixels / m.ydpi.toDouble()
            String.format(Locale.US, "%.1f 英寸", Math.sqrt(w * w + h * h))
        }
    } catch (_: Throwable) {
        "-"
    }

    // ---------------------------------------------------------------- 截图

    /**
     * 截当前应用窗口。
     *
     * 车机的显示管线经常让 PixelCopy 返回一张全黑/全透明的图（甚至直接失败），
     * 所以先用 PixelCopy、结果可疑时再回退到「把 View 画进 Canvas」。
     */
    private suspend fun captureWindow(activity: Activity): Bitmap? {
        val view = activity.window.decorView
        if (view.width <= 0 || view.height <= 0) return null

        val copied = pixelCopy(activity, view.width, view.height)
        if (copied != null && !looksBlank(copied)) return copied
        copied?.recycle()

        return drawView(view, view.width, view.height)
    }

    private suspend fun pixelCopy(activity: Activity, width: Int, height: Int): Bitmap? =
        suspendCancellableCoroutine { cont ->
            try {
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                PixelCopy.request(
                    activity.window,
                    bmp,
                    { result -> cont.resume(if (result == PixelCopy.SUCCESS) bmp else null) },
                    Handler(Looper.getMainLooper()),
                )
            } catch (_: Throwable) {
                cont.resume(null)
            }
        }

    /** 把 View 直接画到软件 Canvas 上（不依赖显示管线，最稳）。 */
    private fun drawView(view: android.view.View, width: Int, height: Int): Bitmap? = try {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        if (looksBlank(bmp)) {
            bmp.recycle()
            null
        } else {
            bmp
        }
    } catch (_: Throwable) {
        null
    }

    /** 抽样判断是不是全黑/全透明——那种图在报告里没有意义。 */
    private fun looksBlank(bmp: Bitmap): Boolean {
        var first = Int.MIN_VALUE
        val stepX = (bmp.width / 12).coerceAtLeast(1)
        val stepY = (bmp.height / 16).coerceAtLeast(1)
        var x = stepX
        while (x < bmp.width) {
            var y = stepY
            while (y < bmp.height) {
                val px = bmp.getPixel(x, y)
                val alpha = (px ushr 24) and 0xFF
                val rgb = px and 0x00FFFFFF
                if (alpha != 0 && rgb != 0) return false
                if (first == Int.MIN_VALUE) first = px
                y += stepY
            }
            x += stepX
        }
        return true
    }

    // ---------------------------------------------------------------- 落盘

    /** 写入系统下载目录；成功返回文件名/路径，失败返回 null。 */
    fun saveToDownloads(ctx: Context, fileName: String, bytes: ByteArray, mime: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                null
            } else {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                fileName
            }
        } else {
            val dir = downloadsDir()
            val f = File(dir, fileName)
            f.outputStream().use { it.write(bytes) }
            f.absolutePath
        }
    } catch (_: Throwable) {
        null
    }
}
