package com.appconfig.injector.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 用系统浏览器打开一个链接。
 *
 * 车机上可能没有浏览器，或者设备策略禁止跳转，这种情况下安静地失败即可，
 * 不要让「关于」页面崩掉。
 */
fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        // 没有可用浏览器时忽略
    }
}
