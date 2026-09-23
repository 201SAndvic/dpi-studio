package com.appconfig.injector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 主色：在 Miuix 默认蓝的基础上压深一点，更沉稳。 */
private val SeedLight = Color(0xFF3A6BF5)
private val SeedLightVariant = Color(0xFF2C55D8)
private val SeedDark = Color(0xFF6C97FF)
private val SeedDarkVariant = Color(0xFF3A6BF5)

@Composable
fun AppTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) {
        darkColorScheme(
            primary = SeedDark,
            primaryVariant = SeedDarkVariant,
        )
    } else {
        lightColorScheme(
            primary = SeedLight,
            primaryVariant = SeedLightVariant,
        )
    }
    MiuixTheme(colors = colors, content = content)
}
