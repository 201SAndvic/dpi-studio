package com.appconfig.injector

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text

/**
 * 在指定屏幕上弹一个很大的提示，用来辨认「这块屏到底是哪块」。
 * 由 [showOn] 通过 ActivityOptions.setLaunchDisplayId 拉起。
 */
class IdentifyActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_NAME = "name"
        private const val EXTRA_DETAIL = "detail"
        private const val EXTRA_ID = "displayId"

        /** 在 displayId 这块屏上显示提示。失败返回 false。 */
        fun showOn(ctx: Context, displayId: Int, name: String, detail: String): Boolean = try {
            val intent = Intent(ctx, IdentifyActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_DETAIL, detail)
                putExtra(EXTRA_ID, displayId)
            }
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
            ctx.startActivity(intent, options)
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val name = intent.getStringExtra(EXTRA_NAME) ?: "?"
        val detail = intent.getStringExtra(EXTRA_DETAIL) ?: ""
        val id = intent.getIntExtra(EXTRA_ID, -1)
        setContent {
            IdentifyScreen(
                name = name,
                detail = detail,
                displayId = id,
                onClose = { finish() },
            )
        }
        // 兜底：没人关就自己关掉，免得一直盖着那块屏
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, 15_000)
    }
}

@Composable
private fun IdentifyScreen(
    name: String,
    detail: String,
    displayId: Int,
    onClose: () -> Unit,
) {
    var left by remember { mutableIntStateOf(8) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
        }
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2C55D8))
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "这块屏幕是",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = name,
                color = Color.White,
                fontSize = 84.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = detail,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "displayId $displayId",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "点按任意位置关闭（$left 秒后自动关闭）",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
