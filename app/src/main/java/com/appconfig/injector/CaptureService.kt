package com.appconfig.injector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * 只用来截图的无障碍服务。
 *
 * Android 上「截取指定的某一块屏幕」在不 root 的前提下只能走无障碍的截图能力
 * （AccessibilityService#takeScreenshot(displayId)，Android 11+）。
 * 开了这个服务之后，主页的「截图」按钮就能截任意一块屏幕。
 */
class CaptureService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要监听事件
    }

    override fun onInterrupt() {
        // 不需要中断处理
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: CaptureService? = null
            private set

        /** 服务是否已经在运行。 */
        fun isReady(): Boolean = instance != null

        /** 是否已经在系统里启用（可能还没连上）。 */
        fun isEnabled(ctx: Context): Boolean {
            if (instance != null) return true
            val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as? android.view.accessibility.AccessibilityManager ?: return false
            val enabled = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            return enabled.any { it.resolveInfo?.serviceInfo?.packageName == ctx.packageName }
        }

        fun openSettings(ctx: Context) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {
                // 车机可能把无障碍设置页裁掉了，交给界面提示兜底
            }
        }
    }
}
