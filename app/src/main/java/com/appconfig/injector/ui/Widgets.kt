package com.appconfig.injector.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appconfig.injector.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 当前应用版本号（来自 build.gradle.kts 的 versionName）。 */
internal fun appVersionName(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
} catch (_: Exception) {
    "?"
}

/** 应用图标：自适应图标不能直接用 painterResource，这里把背景层和前景层叠起来画。 */
@Composable
fun AppIcon(size: Dp, corner: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner)),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** 页面大标题：大号、加粗、左对齐，跟着内容一起滚动。 */
@Composable
fun PageTitle(text: String) {
    Text(
        text = text,
        // 32dp = 卡片外留白 16 + 卡片内留白 16，和卡片里的正文对齐（同参考图）
        modifier = Modifier.padding(start = 32.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
        style = MiuixTheme.textStyles.title1,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onBackground,
    )
}

/** 统一卡片：白底、大圆角、无阴影；卡片之间靠间距分隔，不画横线。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = PaddingValues(vertical = 4.dp),
    container: Color = MiuixTheme.colorScheme.surfaceContainer,
    onContainer: Color = MiuixTheme.colorScheme.onSurfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        cornerRadius = 20.dp,
        insideMargin = insideMargin,
        colors = CardDefaults.defaultColors(color = container, contentColor = onContainer),
        content = content,
    )
}

/** 卡片上方的小节标题：灰色、左对齐，左边缘与卡片内正文对齐。 */
@Composable
fun SectionHeader(text: String) {
    SmallTitle(
        text = text,
        modifier = Modifier.padding(start = 32.dp, end = 20.dp),
        textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        insideMargin = PaddingValues(top = 18.dp, bottom = 6.dp),
    )
}

/** 只读信息行。图标统一黑色单色，标题加粗，说明灰色。 */
@Composable
fun InfoRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    startIcon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (startIcon != null) {
            Icon(
                imageVector = startIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.SemiBold,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 可点击行：黑色图标 + 加粗标题 + 灰色说明 + 右侧三角。 */
@Composable
fun ClickableRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    startIcon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (startIcon != null) {
            Icon(
                imageVector = startIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) MiuixTheme.colorScheme.onSurface
                else MiuixTheme.colorScheme.disabledOnSurface,
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MiuixTheme.colorScheme.onSurface
                else MiuixTheme.colorScheme.disabledOnSurface,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            modifier = Modifier.size(width = 9.dp, height = 15.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
}

/** 大数字卡片：主页的统计块。 */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    container: Color = MiuixTheme.colorScheme.surfaceContainer,
    onContainer: Color = MiuixTheme.colorScheme.onSurfaceContainer,
) {
    Card(
        modifier = modifier,
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(color = container, contentColor = onContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = value,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 通用确认弹窗（Miuix 风格）。 */
/** 二级菜单里的一行选项。 */
@Composable
fun DialogOptionRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MiuixTheme.textStyles.main)
    }
}

/** 通用二级菜单（OverlayDialog + 选项行）。 */
@Composable
fun OptionMenu(
    show: Boolean,
    title: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                DialogOptionRow(option) {
                    onDismiss()
                    onSelect(index)
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    show: Boolean,
    title: String,
    summary: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = dismissText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = confirmText,
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
