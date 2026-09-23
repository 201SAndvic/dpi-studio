package com.appconfig.injector.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appconfig.injector.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(
    appVersion: String,
    selfMinWidth: Int,
    onBack: () -> Unit,
    onGithub: () -> Unit,
) {
    val ctx = LocalContext.current
    var showLicense by remember { mutableStateOf(false) }

    Scaffold(containerColor = MiuixTheme.colorScheme.surface) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // 顶部一行：返回按钮（和 Sukisu Ultra 的图标行一致）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, top = 6.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowRight,
                        contentDescription = ctx.getString(R.string.about_back),
                        modifier = Modifier
                            .size(width = 11.dp, height = 17.dp)
                            .rotate(180f),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }

            PageTitle(ctx.getString(R.string.about_title))

            // 图标 + 名称 + 版本
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(size = 88.dp, corner = 22.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = ctx.getString(R.string.app_name),
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "v$appVersion",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }

            AppCard {
                ClickableRow(
                    title = ctx.getString(R.string.about_github),
                    summary = ctx.getString(R.string.about_github_desc),
                    startIcon = MiuixIcons.Medium.Link,
                    onClick = onGithub,
                )
                ClickableRow(
                    title = ctx.getString(R.string.about_license),
                    summary = ctx.getString(R.string.about_license_desc),
                    startIcon = MiuixIcons.Medium.Info,
                    onClick = { showLicense = true },
                )
            }

            AppCard {
                InfoRow(title = ctx.getString(R.string.about_engine), value = "JingMatrix/LSPatch v1.2")
                InfoRow(
                    title = ctx.getString(R.string.about_module),
                    value = ctx.getString(R.string.about_module_value),
                )
                InfoRow(title = ctx.getString(R.string.about_ui), value = ctx.getString(R.string.about_ui_value))
                InfoRow(
                    title = ctx.getString(R.string.settings_self_size),
                    value = if (selfMinWidth > 0) {
                        ctx.getString(R.string.settings_self_size_value, selfMinWidth)
                    } else {
                        ctx.getString(R.string.settings_self_size_follow)
                    },
                )
                InfoRow(
                    title = ctx.getString(R.string.about_sign),
                    value = ctx.getString(R.string.about_sign_value),
                )
            }

            Text(
                text = ctx.getString(R.string.about_howto),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }

    OverlayDialog(
        show = showLicense,
        title = ctx.getString(R.string.about_license),
        onDismissRequest = { showLicense = false },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = ctx.getString(R.string.about_license_text),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            TextButton(
                text = ctx.getString(R.string.close),
                onClick = { showLicense = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }
    }
}
