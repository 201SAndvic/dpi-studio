package com.appconfig.injector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 轻量提示条，替代系统 Toast。
 *
 * 系统 Toast 最短就是 2 秒（LENGTH_SHORT），没法再短；这里自己画一个，
 * 固定显示 [SHOW_MS] 毫秒。
 */
object Messages {

    const val SHOW_MS = 1500L

    private var seq = 0

    var current by mutableStateOf<Pair<Int, String>?>(null)
        private set

    fun show(text: String) {
        seq++
        current = seq to text
    }

    fun clear() {
        current = null
    }
}

/** 放在界面最上层，显示当前提示。 */
@Composable
fun MessageOverlay() {
    val message = Messages.current ?: return
    LaunchedEffect(message.first) {
        delay(Messages.SHOW_MS)
        if (Messages.current?.first == message.first) Messages.clear()
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 72.dp, start = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = message.second,
                color = Color.White,
                style = MiuixTheme.textStyles.body2,
                textAlign = TextAlign.Center,
            )
        }
    }
}
