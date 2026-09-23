package com.appconfig.injector.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.appconfig.injector.Config
import com.appconfig.injector.Injector
import com.appconfig.injector.Prefs
import com.appconfig.injector.R

@Composable
fun InjectScreen(state: AppState, actions: AppActions) {
    val ctx = LocalContext.current
    var showAdvanced by remember { mutableStateOf(false) }
    var showSaveMenu by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(ctx.getString(R.string.inject_status_idle)) }

    // 当前参数
    AppCard {
        InfoRow(
            title = ctx.getString(R.string.current_config),
            summary = state.config.summary(),
            startIcon = MiuixIcons.Medium.Tune,
        )
    }

    // 目标应用
    AppCard {
        InfoRow(
            title = ctx.getString(R.string.inject_target),
            value = state.targetName.ifEmpty { ctx.getString(R.string.inject_target_none) },
            startIcon = MiuixIcons.Medium.File,
        )
        ClickableRow(
            title = ctx.getString(R.string.inject_pick),
            summary = ctx.getString(R.string.inject_pick_desc),
            onClick = actions.pickTarget,
        )
    }

    // 尺寸
    SectionHeader(ctx.getString(R.string.inject_size))
    AppCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(text = ctx.getString(R.string.inject_min_width), style = MiuixTheme.textStyles.main)
            Text(
                text = ctx.getString(R.string.inject_min_width_helper),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(10.dp))
            ConfigTextField(
                label = ctx.getString(R.string.inject_min_width),
                value = state.config.minWidth,
                keyboardType = KeyboardType.Number,
                filter = { ch -> ch.isDigit() },
            ) { text -> state.update { it.minWidth = text } }

            Spacer(Modifier.height(6.dp))
            val sliderValue = (state.config.minWidth.toFloatOrNull() ?: 640f).coerceIn(200f, 1200f)
            top.yukonga.miuix.kmp.basic.Slider(
                value = sliderValue,
                onValueChange = { v ->
                    val rounded = (Math.round(v / 10f) * 10).toString()
                    state.update { it.minWidth = rounded }
                },
                valueRange = 200f..1200f,
                steps = 99,
            )

            Spacer(Modifier.height(6.dp))
            Text(
                text = ctx.getString(R.string.inject_quick),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            // 常用值：两行等宽铺满，避免横向滚动被卡片裁掉
            val quickValues = listOf("320", "400", "480", "560", "640", "720", "840")
            quickValues.chunked(4).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { v ->
                        TextButton(
                            text = v,
                            onClick = { state.update { it.minWidth = v } },
                            modifier = Modifier.weight(1f),
                            minWidth = 0.dp,
                            minHeight = 40.dp,
                            insideMargin = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 4.dp,
                                vertical = 8.dp,
                            ),
                        )
                    }
                    repeat(4 - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // 推荐注入配置：跟随「本机显示尺寸」，也就是用户第一次引导里设的那个值
    val self = state.selfMinWidth
    SectionHeader(ctx.getString(R.string.inject_recommend))
    AppCard {
        if (self > 0) {
            PresetRow(
                name = ctx.getString(R.string.inject_recommend),
                summary = ctx.getString(R.string.inject_recommend_desc, self),
                config = recommendConfig(self),
                onApply = {
                    state.applyConfig(recommendConfig(self))
                    actions.toast("${ctx.getString(R.string.preset_applied)}：$self dp")
                },
            )
        } else {
            InfoRow(
                title = ctx.getString(R.string.inject_recommend),
                summary = ctx.getString(R.string.inject_recommend_none),
            )
        }
    }

    // 预设
    SectionHeader(ctx.getString(R.string.inject_presets))
    AppCard {
        InfoRow(title = ctx.getString(R.string.preset_builtin))
        BuiltinPresets.forEach { preset ->
            PresetRow(
                name = preset.name,
                config = preset.builder(),
                onApply = {
                    state.applyConfig(preset.builder())
                    actions.toast("${ctx.getString(R.string.preset_applied)}：${preset.name}")
                },
            )
        }

        InfoRow(title = ctx.getString(R.string.preset_mine))
        if (state.presets.length() == 0) {
            InfoRow(title = ctx.getString(R.string.preset_empty))
        } else {
            for (i in 0 until state.presets.length()) {
                val obj: JSONObject = state.presets.optJSONObject(i) ?: continue
                val name = obj.optString("name", "预设")
                PresetRow(
                    name = name,
                    config = Config.fromJson(obj.optJSONObject("config")),
                    onApply = {
                        state.applyConfig(Config.fromJson(obj.optJSONObject("config")))
                        actions.toast("${ctx.getString(R.string.preset_applied)}：$name")
                    },
                    onDelete = { state.removePreset(name) },
                )
            }
        }
    }

    // 高级参数
    AppCard {
        ClickableRow(
            title = ctx.getString(R.string.inject_advanced),
            value = ctx.getString(
                if (showAdvanced) R.string.inject_collapse else R.string.inject_expand
            ),
            startIcon = MiuixIcons.Medium.Tune,
            onClick = { showAdvanced = !showAdvanced },
        )
        if (showAdvanced) {
            SwitchRow(
                title = ctx.getString(R.string.inject_sw_fake_app_list),
                summary = ctx.getString(R.string.inject_sw_fake_app_list_desc),
                checked = state.config.fakeAppList,
                onCheckedChange = { v -> state.update { it.fakeAppList = v } },
            )
            SwitchRow(
                title = ctx.getString(R.string.inject_sw_round),
                summary = ctx.getString(R.string.inject_sw_round_desc),
                checked = state.config.round,
                onCheckedChange = { v -> state.update { it.round = v } },
            )
            SwitchRow(
                title = ctx.getString(R.string.inject_sw_force_round),
                summary = ctx.getString(R.string.inject_sw_force_round_desc),
                checked = state.config.forceRound,
                onCheckedChange = { v -> state.update { it.forceRound = v } },
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                ConfigTextField(
                    label = ctx.getString(R.string.inject_round_size),
                    value = state.config.roundSize,
                    keyboardType = KeyboardType.Decimal,
                    filter = ::isDecimalInput,
                ) { text -> state.update { it.roundSize = text } }
                ConfigTextField(
                    label = ctx.getString(R.string.inject_round_ratio),
                    value = state.config.roundRatio,
                    keyboardType = KeyboardType.Decimal,
                    filter = ::isDecimalInput,
                ) { text -> state.update { it.roundRatio = text } }
                ConfigTextField(
                    label = ctx.getString(R.string.inject_h_offset),
                    value = state.config.hOffset,
                    keyboardType = KeyboardType.Decimal,
                    filter = ::isDecimalInput,
                ) { text -> state.update { it.hOffset = text } }
                ConfigTextField(
                    label = ctx.getString(R.string.inject_v_offset),
                    value = state.config.vOffset,
                    keyboardType = KeyboardType.Decimal,
                    filter = ::isDecimalInput,
                ) { text -> state.update { it.vOffset = text } }
                ConfigTextField(
                    label = ctx.getString(R.string.inject_bg_color),
                    value = state.config.bgColor,
                    keyboardType = KeyboardType.Text,
                ) { text -> state.update { it.bgColor = text } }
                ConfigTextField(
                    label = ctx.getString(R.string.inject_bg_alpha),
                    value = state.config.bgAlpha,
                    keyboardType = KeyboardType.Number,
                    filter = { ch -> ch.isDigit() || ch == '-' },
                ) { text -> state.update { it.bgAlpha = text } }
            }
            ChoiceRow(
                title = ctx.getString(R.string.inject_toast),
                options = listOf("never", "once", "always"),
                selectedIndex = listOf("never", "once", "always")
                    .indexOf(state.config.toast).coerceAtLeast(0),
                onSelect = { i -> state.update { it.toast = listOf("never", "once", "always")[i] } },
            )
        }
    }

    // 注入选项
    SectionHeader(ctx.getString(R.string.inject_options))
    AppCard {
        ChoiceRow(
            title = ctx.getString(R.string.inject_sig_bypass),
            summary = ctx.getString(R.string.inject_sig_bypass_desc),
            options = listOf("0", "1", "2", "3"),
            selectedIndex = state.config.sigBypass.coerceIn(0, 3),
            onSelect = { i -> state.update { it.sigBypass = i } },
        )
        SwitchRow(
            title = ctx.getString(R.string.inject_dex),
            summary = ctx.getString(R.string.inject_dex_desc),
            checked = state.config.injectDex,
            onCheckedChange = { v -> state.update { it.injectDex = v } },
        )
    }

    // 动作
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = status,
            style = MiuixTheme.textStyles.main,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 10.dp),
        )
        Button(
            onClick = {
                if (running) return@Button
                val target = state.targetFile
                if (target == null || !target.isFile) {
                    actions.toast("请先选择目标 APK")
                    return@Button
                }
                val bad = state.config.validate()
                if (bad != null) {
                    actions.toast(bad)
                    return@Button
                }
                running = true
                status = ctx.getString(R.string.inject_working)
                val snapshot = state.config.copy()
                Injector.start(ctx, target, state.targetName, snapshot) { apk, error ->
                    running = false
                    if (apk != null) {
                        state.setOutput(apk)
                        status = "${ctx.getString(R.string.inject_done)}：${apk.name}"
                        state.addHistoryEntry(state.targetName, true, snapshot, apk)
                        actions.toast("注入完成，可保存成品 APK")
                    } else {
                        status = "注入失败：$error"
                        state.addHistoryEntry(state.targetName, false, snapshot, null)
                        actions.toast("注入失败：$error")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !running,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(
                text = ctx.getString(R.string.inject_start),
                style = MiuixTheme.textStyles.button,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onPrimary,
            )
        }
        if (running) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }

    AppCard {
        ClickableRow(
            title = ctx.getString(R.string.inject_save),
            summary = state.lastOutput?.name ?: ctx.getString(R.string.inject_save_desc),
            startIcon = MiuixIcons.Medium.Download,
            enabled = state.lastOutput != null,
            onClick = { showSaveMenu = true },
        )
        ClickableRow(
            title = ctx.getString(R.string.inject_export_xml),
            summary = ctx.getString(R.string.inject_export_xml_desc),
            startIcon = MiuixIcons.Medium.ConvertFile,
            onClick = actions.exportXml,
        )
    }

    // 保存位置：默认下载目录，也可以自己挑文件夹
    OptionMenu(
        show = showSaveMenu,
        title = ctx.getString(R.string.inject_save),
        options = listOf(
            ctx.getString(R.string.save_to_downloads),
            ctx.getString(R.string.save_pick_folder),
        ),
        onSelect = { index ->
            if (index == 0) actions.saveToDownloads() else actions.pickSaveFolder()
        },
        onDismiss = { showSaveMenu = false },
    )
}

// ---------------------------------------------------------------- 小组件

private class BuiltinPreset(val name: String, val builder: () -> Config)

private val BuiltinPresets = listOf(
    BuiltinPreset("车机 · 3:4 方屏") {
        Config().apply { minWidth = "640"; round = false; forceRound = false }
    },
    BuiltinPreset("手机 · 铺满无黑边") {
        Config().apply { minWidth = "480"; round = false; forceRound = false }
    },
    BuiltinPreset("平板 · 常规方屏") {
        Config().apply { minWidth = "560"; round = false; forceRound = false }
    },
    BuiltinPreset("默认值") {
        Config().apply { minWidth = "320"; round = false; forceRound = false }
    },
)

@Composable
private fun PresetRow(
    name: String,
    config: Config,
    onApply: () -> Unit,
    onDelete: (() -> Unit)? = null,
    summary: String? = null,
) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = name, style = MiuixTheme.textStyles.main)
            Text(
                text = summary ?: config.summary(),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        TextButton(
            text = ctx.getString(R.string.preset_apply),
            onClick = onApply,
            minWidth = 0.dp,
            minHeight = 34.dp,
            insideMargin = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 14.dp,
                vertical = 5.dp,
            ),
        )
        if (onDelete != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(
                text = ctx.getString(R.string.preset_delete),
                onClick = onDelete,
                minWidth = 0.dp,
                minHeight = 34.dp,
                insideMargin = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 14.dp,
                    vertical = 5.dp,
                ),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MiuixTheme.textStyles.main)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    summary: String? = null,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    ClickableRow(
        title = title,
        summary = summary,
        value = options.getOrNull(selectedIndex),
        onClick = { show = true },
    )
    OverlayDialog(
        show = show,
        title = title,
        onDismissRequest = { show = false },
    ) {
        Column(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            show = false
                            onSelect(index)
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option,
                        style = MiuixTheme.textStyles.main,
                        modifier = Modifier.weight(1f),
                    )
                    if (index == selectedIndex) {
                        Icon(
                            imageVector = MiuixIcons.Basic.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigTextField(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    filter: ((Char) -> Boolean)? = null,
    onValueChange: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    LaunchedEffect(value) { text = value }
    TextField(
        value = TextFieldValue(text, TextRange(text.length)),
        onValueChange = { tfv ->
            val filtered = if (filter == null) tfv.text else tfv.text.filter(filter)
            text = filtered
            onValueChange(filtered)
        },
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    )
}

private fun isDecimalInput(ch: Char): Boolean =
    ch.isDigit() || ch == '.' || ch == '-'

/** 推荐配置：就用用户为本机设的那个宽度，关掉圆屏适配（跟站内默认一致）。 */
private fun recommendConfig(width: Int): Config = Config().apply {
    minWidth = width.toString()
    round = false
    forceRound = false
}
