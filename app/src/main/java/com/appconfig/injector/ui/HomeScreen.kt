package com.appconfig.injector.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.appconfig.injector.DeviceInfo
import com.appconfig.injector.Prefs
import com.appconfig.injector.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.ScreenCapture
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(state: AppState, actions: AppActions) {
    val ctx = LocalContext.current
    val minWidth = state.config.minWidth.trim().toIntOrNull() ?: 0
    val displays = remember(state.config.minWidth) { DeviceInfo.displays(ctx, minWidth) }

    HeroRow(ctx, displays, state.history.length())

    SectionHeader(ctx.getString(R.string.section_device))
    AppCard {
        val items = remember(state.config.minWidth) {
            DeviceInfo.device(ctx, appVersionName(ctx), state.history.length())
        }
        items.forEach { item ->
            InfoRow(title = item.label, value = item.value)
        }
    }

    SectionHeader(ctx.getString(R.string.section_display))
    DisplayCard(ctx, displays, actions)
}

@Composable
private fun DisplayCard(
    ctx: Context,
    displays: List<DeviceInfo.DisplayInfo>,
    actions: AppActions,
) {
    // 改过名字之后靠它触发刷新
    var namesVersion by remember { mutableIntStateOf(0) }
    var menuTarget by remember { mutableStateOf<DeviceInfo.DisplayInfo?>(null) }
    var renameTarget by remember { mutableStateOf<DeviceInfo.DisplayInfo?>(null) }
    var renameInitial by remember { mutableStateOf("") }
    var needAccess by remember { mutableStateOf(false) }
    var shotTarget by remember { mutableStateOf<DeviceInfo.DisplayInfo?>(null) }
    val scope = rememberCoroutineScope()

    AppCard {
        if (displays.isEmpty()) {
            InfoRow(title = ctx.getString(R.string.no_display))
            return@AppCard
        }
        displays.forEach { display ->
            val name = remember(namesVersion, display.displayId) {
                Prefs.displayName(ctx, display.displayId) ?: display.title
            }
            val resolution = display.items
                .firstOrNull { it.label == ctx.getString(R.string.display_resolution) }
                ?.value.orEmpty()
            val dpi = display.items
                .firstOrNull { it.label == ctx.getString(R.string.display_density) }
                ?.value.orEmpty()

            DisplayHeaderRow(
                name = name,
                summary = listOf(resolution, dpi).filter { it.isNotEmpty() }.joinToString(" · "),
                onClick = { menuTarget = display },
            )
            // 分辨率与密度已经写在标题行里了，这里不再重复一行
            display.items
                .filterNot { it.label == ctx.getString(R.string.display_resolution) }
                .filterNot { it.label == ctx.getString(R.string.display_density) }
                .forEach { item ->
                    InfoRow(title = item.label, value = item.value)
                }
        }
    }

    // 二级菜单：显示 / 截图 / 重命名
    menuTarget?.let { display ->
        val name = Prefs.displayName(ctx, display.displayId) ?: display.title
        val resolution = display.items
            .firstOrNull { it.label == ctx.getString(R.string.display_resolution) }
            ?.value.orEmpty()
        val dpi = display.items
            .firstOrNull { it.label == ctx.getString(R.string.display_density) }
            ?.value.orEmpty()
        OptionMenu(
            show = true,
            title = name,
            options = listOf(
                ctx.getString(R.string.display_show),
                ctx.getString(R.string.display_shot),
                ctx.getString(R.string.display_rename),
            ),
            onSelect = { index ->
                when (index) {
                    0 -> {
                        val ok = ScreenTools.showIdentify(
                            ctx,
                            display.displayId,
                            name,
                            "$resolution   $dpi",
                        )
                        actions.toast(
                            if (ok) {
                                ctx.getString(R.string.display_show_tip)
                            } else {
                                ctx.getString(R.string.display_show_failed, display.displayId, "")
                            }
                        )
                    }

                    1 -> if (ScreenTools.accessibilityEnabled(ctx)) {
                        startCapture(ctx, display.displayId, name, scope, actions)
                    } else {
                        // 截指定屏幕需要权限，先提示
                        shotTarget = display
                        needAccess = true
                    }

                    else -> {
                        renameInitial = name
                        renameTarget = display
                    }
                }
            },
            onDismiss = { menuTarget = null },
        )
    }

    renameTarget?.let { display ->
        RenameScreenDialog(
            initial = renameInitial,
            onConfirm = { newName ->
                Prefs.setDisplayName(ctx, display.displayId, newName)
                namesVersion++
                renameTarget = null
            },
            onReset = {
                Prefs.setDisplayName(ctx, display.displayId, null)
                namesVersion++
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    if (needAccess) {
        OverlayDialog(
            show = true,
            title = ctx.getString(R.string.display_shot),
            summary = ctx.getString(R.string.display_shot_need_access),
            onDismissRequest = { needAccess = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        needAccess = false
                        ScreenTools.openAccessibilitySettings(ctx)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = ctx.getString(R.string.display_shot_go_access),
                        style = MiuixTheme.textStyles.button,
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
                TextButton(
                    text = ctx.getString(R.string.display_shot_try_anyway),
                    onClick = {
                        needAccess = false
                        shotTarget?.let { d ->
                            val n = Prefs.displayName(ctx, d.displayId) ?: d.title
                            startCapture(ctx, d.displayId, n, scope, actions)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun DisplayHeaderRow(
    name: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.Medium.ScreenCapture,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.SemiBold,
            )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
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

/** 截取某块屏幕，并把结果提示出来。 */
private fun startCapture(
    ctx: Context,
    displayId: Int,
    name: String,
    scope: CoroutineScope,
    actions: AppActions,
) {
    actions.toast(ctx.getString(R.string.display_shot_saving))
    scope.launch {
        when (val result = ScreenTools.captureToDownloads(ctx, displayId, name)) {
            is CaptureOutcome.Ok ->
                actions.toast(ctx.getString(R.string.display_shot_done, result.name))

            is CaptureOutcome.Fail ->
                actions.toast(ctx.getString(R.string.display_shot_failed, result.reason))

            CaptureOutcome.NeedAccess -> {
                actions.toast(ctx.getString(R.string.display_shot_need_access))
                ScreenTools.openAccessibilitySettings(ctx)
            }
        }
    }
}

@Composable
private fun RenameScreenDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(initial) }
    OverlayDialog(
        show = true,
        title = ctx.getString(R.string.display_rename),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            TextField(
                value = TextFieldValue(text, TextRange(text.length)),
                onValueChange = { text = it.text },
                label = ctx.getString(R.string.display_rename_hint),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                text = ctx.getString(R.string.display_rename_reset),
                onClick = onReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = ctx.getString(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = ctx.getString(R.string.confirm),
                    onClick = { onConfirm(text.trim()) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun HeroRow(ctx: Context, displays: List<DeviceInfo.DisplayInfo>, records: Int) {
    val primary = displays.firstOrNull()
    val heroTitle = primary?.let { d ->
        Prefs.displayName(ctx, d.displayId) ?: d.title
    } ?: ctx.getString(R.string.display_primary)
    val heroResolution = primary?.items
        ?.firstOrNull { it.label == ctx.getString(R.string.display_resolution) }
        ?.value ?: ctx.getString(R.string.no_display)
    val heroArea = primary?.items
        ?.firstOrNull { it.label == ctx.getString(R.string.display_dp) }
        ?.value ?: "-"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val onHero = MiuixTheme.colorScheme.onPrimaryContainer
        Card(
            modifier = Modifier
                .weight(1.2f)
                .height(158.dp),
            cornerRadius = 20.dp,
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.primaryContainer,
                contentColor = onHero,
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                // 右下角的大号淡色水印图标，和 Sukisu Ultra 的状态卡一样
                Icon(
                    imageVector = MiuixIcons.Medium.ScreenCapture,
                    contentDescription = null,
                    modifier = Modifier
                        .size(88.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 14.dp, y = 16.dp),
                    tint = onHero.copy(alpha = 0.20f),
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                ) {
                    Text(
                        text = heroTitle,
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = heroResolution,
                        style = MiuixTheme.textStyles.body2,
                        color = onHero.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = ctx.getString(R.string.hero_display_area),
                        style = MiuixTheme.textStyles.body2,
                        color = onHero.copy(alpha = 0.85f),
                    )
                    Text(
                        text = heroArea,
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .height(158.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                label = ctx.getString(R.string.hero_records),
                value = records.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = ctx.getString(R.string.hero_engine),
                value = "v1.2",
                modifier = Modifier.weight(1f),
            )
        }
    }
}
