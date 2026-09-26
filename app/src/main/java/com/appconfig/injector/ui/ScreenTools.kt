package com.appconfig.injector.ui

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityWindowInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.appconfig.injector.CaptureService
import com.appconfig.injector.IdentifyActivity
import com.appconfig.injector.Logs
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** 截图结果。 */
sealed interface CaptureOutcome {
    data class Ok(val name: String) : CaptureOutcome
    data class Fail(val reason: String) : CaptureOutcome
    data object NeedAccess : CaptureOutcome
}

/**
 * 屏幕相关动作：在指定屏幕上打标记、截取指定屏幕。
 *
 * 「截取指定屏幕」在 Android 上没有普通应用可直接用的公开接口，这里按可靠性排三级：
 *   1. root（su + screencap -d）—— 有 root 直接可用
 *   2. 无障碍服务截图（AccessibilityService#takeScreenshot(displayId)，Android 11+）
 *   3. 都没有就提示用户去开无障碍
 */
object ScreenTools {

    /** 在 displayId 这块屏上弹一个大提示，用来辨认是哪块屏。 */
    fun showIdentify(ctx: Context, displayId: Int, name: String, detail: String): Boolean =
        IdentifyActivity.showOn(ctx, displayId, name, detail)

    fun openAccessibilitySettings(ctx: Context) = CaptureService.openSettings(ctx)

    fun accessibilityReady(): Boolean = CaptureService.isReady()

    fun accessibilityEnabled(ctx: Context): Boolean = CaptureService.isEnabled(ctx)

    /** 截取某块屏幕并存进下载目录。 */
    suspend fun captureToDownloads(
        ctx: Context,
        displayId: Int,
        displayName: String,
    ): CaptureOutcome {
        // 文件名带上「这块屏当前前台应用」，方便归档：
        //   DPI工坊-副屏-20260923-150602_飞牛播放器.png
        val appName = withContext(Dispatchers.IO) { foregroundAppLabel(ctx, displayId) }
        val suffix = if (appName.isNullOrBlank()) "" else "_" + safeName(appName)
        val fileName = "DPI工坊-" + safeName(displayName) + "-" + stamp() + suffix + ".png"

        // 1) root
        val tmp = File(ctx.cacheDir, "shot-$displayId.png")
        if (captureViaRoot(displayId, tmp)) {
            val saved = withContext(Dispatchers.IO) {
                saveFileToDownloads(ctx, fileName, tmp, "image/png")
            }
            tmp.delete()
            return if (saved != null) {
                CaptureOutcome.Ok(saved)
            } else {
                CaptureOutcome.Fail("无法写入下载目录")
            }
        }

        // 2) 无障碍
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bmp = captureViaAccessibility(displayId)
            if (bmp != null) {
                val bytes = ByteArrayOutputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray()
                }
                bmp.recycle()
                if (bytes.isEmpty()) return CaptureOutcome.Fail("截图内容为空")
                val saved = withContext(Dispatchers.IO) {
                    saveBytesToDownloads(ctx, fileName, bytes, "image/png")
                }
                return if (saved != null) {
                    CaptureOutcome.Ok(saved)
                } else {
                    CaptureOutcome.Fail("无法写入下载目录")
                }
            }
        }

        return CaptureOutcome.NeedAccess
    }

    // ---------------------------------------------------------------- root

    private suspend fun captureViaRoot(displayId: Int, out: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                out.delete()
                val cmd = "screencap -d $displayId -p ${out.absolutePath}"
                val process = ProcessBuilder("su", "-c", cmd)
                    .redirectErrorStream(true)
                    .start()
                val finished = process.waitFor(10, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroy()
                    return@withContext false
                }
                process.exitValue() == 0 && out.isFile && out.length() > 0
            } catch (_: Throwable) {
                false
            }
        }

    // ---------------------------------------------------------------- 无障碍

    private suspend fun captureViaAccessibility(displayId: Int): Bitmap? =
        suspendCancellableCoroutine { cont ->
            val service = CaptureService.instance
            if (service == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val main = Handler(Looper.getMainLooper())
            var finished = false
            val timeout = Runnable {
                if (!finished) {
                    finished = true
                    cont.resume(null)
                }
            }
            main.postDelayed(timeout, 8000)

            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    if (finished) return
                    finished = true
                    main.removeCallbacks(timeout)
                    val bitmap = try {
                        val hb = result.hardwareBuffer
                        val wrapped = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                        val copy = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        hb.close()
                        copy
                    } catch (_: Throwable) {
                        null
                    }
                    cont.resume(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    if (finished) return
                    finished = true
                    main.removeCallbacks(timeout)
                    cont.resume(null)
                }
            }

            try {
                service.takeScreenshot(displayId, Executor { it.run() }, callback)
            } catch (_: Throwable) {
                if (!finished) {
                    finished = true
                    main.removeCallbacks(timeout)
                    cont.resume(null)
                }
            }
        }

    // ---------------------------------------------------------------- 小工具

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private fun safeName(name: String): String =
        name.ifBlank { "屏幕" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

    // ---------------------------------------------------------------- 前台应用名

    /**
     * 取某块屏幕上当前前台应用的名称。
     *
     * 无障碍在线时直接问它（能按 displayId 分辨窗口）；否则退回用 root 解析 dumpsys。
     * 都拿不到就返回 null，文件名里就不带应用名。
     */
    private fun foregroundAppLabel(ctx: Context, displayId: Int): String? {
        val fromAccessibility = packageViaAccessibility(displayId)
        val pkg = fromAccessibility ?: packageViaRoot(displayId)
        if (pkg.isNullOrBlank()) {
            Logs.append("[D] 前台应用：display=$displayId 未取到包名")
            return null
        }
        val label = appLabel(ctx, pkg)
        Logs.append(
            "[D] 前台应用：display=$displayId 来源=${if (fromAccessibility != null) "无障碍" else "root"}" +
                " 包名=$pkg 显示名=${label ?: "(取不到，退回包名)"}"
        )
        return label ?: pkg
    }

    /**
     * 取应用的显示名。
     *
     * Android 11 起有包可见性限制：没在清单里声明 queries 或 QUERY_ALL_PACKAGES 时，
     * getApplicationInfo 会抛 NameNotFoundException，那样就只能拿到包名。
     * 这里查不到就返回 null，由调用方决定怎么退化。
     */
    private fun appLabel(ctx: Context, pkg: String): String? {
        val pm = ctx.packageManager
        val info = try {
            pm.getApplicationInfo(pkg, 0)
        } catch (_: Throwable) {
            try {
                pm.getInstalledApplications(0).firstOrNull { it.packageName == pkg }
            } catch (_: Throwable) {
                null
            }
        } ?: return null
        return try {
            pm.getApplicationLabel(info).toString()
                .takeIf { it.isNotBlank() && it != pkg }
        } catch (_: Throwable) {
            null
        }
    }

    private fun packageViaAccessibility(displayId: Int): String? {
        val service = CaptureService.instance ?: return null
        val windows = try {
            service.windows
        } catch (_: Throwable) {
            return null
        } ?: return null

        val onDisplay = windows.filter { w ->
            try {
                w.displayId == displayId
            } catch (_: Throwable) {
                false
            }
        }
        if (onDisplay.isEmpty()) {
            val ids = windows.mapNotNull { w -> runCatching { w.displayId }.getOrNull() }
                .distinct().sorted()
            Logs.append(
                "[D] 前台应用探测：display=$displayId 本屏没有无障碍窗口，现有窗口分布在屏幕 $ids"
            )
            return null
        }

        // 同一块屏上可能同时有主界面和浮窗。只按层级挑会挑到角落里的小浮窗，
        // 所以改成「占屏面积优先、层级次之」。
        fun score(w: AccessibilityWindowInfo): Long {
            val area = try {
                val r = Rect()
                w.getBoundsInScreen(r)
                r.width().toLong() * r.height().toLong()
            } catch (_: Throwable) {
                0L
            }
            return area * 1000L + w.layer
        }

        val appWindows = onDisplay.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val target = (if (appWindows.isNotEmpty()) appWindows else onDisplay)
            .maxByOrNull { score(it) }

        val pkg = try {
            target?.root?.packageName?.toString()
        } catch (_: Throwable) {
            null
        }
        Logs.append(
            "[D] 前台应用探测：display=$displayId 窗口=${windows.size} 本屏窗口=${onDisplay.size} pkg=$pkg"
        )
        return pkg
    }

    /** 包名/类名对，例如 com.example.app/.MainActivity。 */
    private val pkgPattern = Regex("([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)/[A-Za-z0-9_.$]+")

    private fun packageViaRoot(displayId: Int): String? {
        val activityText = runAsRoot("dumpsys activity activities")
        val displaysText = runAsRoot("dumpsys window displays")
        val windowText = runAsRoot("dumpsys window")

        // 1) Android 12 起 dumpsys activity activities 会按屏分段，里面有该屏当前 Activity
        pkgInBlock(
            text = activityText,
            header = Regex("Display #$displayId\\b"),
            next = Regex("\\n\\s*Display #\\d+\\b"),
            markers = listOf("mResumedActivity", "topResumedActivity", "mFocusedApp"),
        )?.let { return it }

        // 2) 老一点的版本：dumpsys window displays 里每块屏有 mCurrentFocus
        pkgInBlock(
            text = displaysText,
            header = Regex("Display: mDisplayId=$displayId\\b"),
            next = Regex("\\n\\s*Display: mDisplayId=\\d+\\b"),
            markers = listOf("mCurrentFocus", "mFocusedApp"),
        )?.let { return it }

        pkgInBlock(
            text = windowText,
            header = Regex("Display: mDisplayId=$displayId\\b"),
            next = Regex("\\n\\s*Display: mDisplayId=\\d+\\b"),
            markers = listOf("mCurrentFocus", "mFocusedApp"),
        )?.let { return it }

        // 3) 兜底：默认屏直接取全局焦点
        if (displayId == 0 && windowText != null) {
            pkgAfterMarker(windowText, listOf("mCurrentFocus", "mFocusedApp"))?.let { return it }
        }

        val all = listOf(activityText, displaysText, windowText).joinToString("\n") { it.orEmpty() }
        Logs.append(
            "[D] 前台应用探测：display=$displayId root 未解析出包名" +
                "（dumpsys 里出现过的屏幕=${displayIdsIn(all)}，" +
                "含 mCurrentFocus=${all.contains("mCurrentFocus")}）"
        )
        return null
    }

    /** 切出某块屏的段落，再在段落里按标记取第一个包名。 */
    private fun pkgInBlock(
        text: String?,
        header: Regex,
        next: Regex,
        markers: List<String>,
    ): String? {
        if (text.isNullOrBlank()) return null
        val start = header.find(text) ?: return null
        val rest = text.substring(start.range.last + 1)
        val stop = next.find(rest)
        val block = if (stop == null) rest else rest.substring(0, stop.range.first)
        return pkgAfterMarker(block, markers)
    }

    private fun pkgAfterMarker(block: String, markers: List<String>): String? {
        for (marker in markers) {
            val idx = block.indexOf(marker)
            if (idx < 0) continue
            val tail = block.substring(idx, minOf(block.length, idx + 800))
            val hit = pkgPattern.find(tail)?.groupValues?.get(1)
            if (!hit.isNullOrBlank()) return hit
        }
        return null
    }

    private fun displayIdsIn(text: String): List<Int> =
        Regex("(?:Display #|Display: mDisplayId=)(\\d+)").findAll(text)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .distinct()
            .sorted()
            .toList()

    private fun runAsRoot(command: String): String? = try {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            null
        } else {
            process.inputStream.bufferedReader().use { it.readText() }
        }
    } catch (_: Throwable) {
        null
    }
}
