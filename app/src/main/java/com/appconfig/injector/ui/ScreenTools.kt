package com.appconfig.injector.ui

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityWindowInfo
import android.content.Context
import android.graphics.Bitmap
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
        val pkg = packageViaAccessibility(displayId) ?: packageViaRoot(displayId)
        if (pkg.isNullOrBlank()) return null
        return try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Throwable) {
            pkg
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
        if (onDisplay.isEmpty()) return null

        // 优先应用窗口，其次任意窗口，都按层级取最上面的
        val target = onDisplay
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .maxByOrNull { it.layer }
            ?: onDisplay.maxByOrNull { it.layer }

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

    private fun packageViaRoot(displayId: Int): String? {
        val text = runAsRoot("dumpsys window displays") ?: return null
        // 在 "Display: mDisplayId=<id>" 这一段里找第一处 mCurrentFocus 的包名
        val block = Regex(
            "Display: mDisplayId=$displayId\\b([\\s\\S]*?)(?=\\n\\s*Display: mDisplayId=|\\z)"
        ).find(text)?.groupValues?.get(1) ?: return null
        return Regex("mCurrentFocus=Window\\{[^}]*?\\s+u\\d+\\s+([A-Za-z0-9_.]+)/")
            .find(block)?.groupValues?.get(1)
    }

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
