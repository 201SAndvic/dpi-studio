package com.appconfig.injector.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.appconfig.injector.Config
import com.appconfig.injector.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(state: AppState, actions: AppActions, onReuse: () -> Unit) {
    val ctx = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    AppCard {
        if (state.history.length() == 0) {
            InfoRow(title = ctx.getString(R.string.history_empty))
            return@AppCard
        }
        for (i in 0 until state.history.length()) {
            val obj: JSONObject = state.history.optJSONObject(i) ?: continue
            HistoryRow(
                obj = obj,
                timeText = fmt.format(Date(obj.optLong("time", System.currentTimeMillis()))),
                onReuse = {
                    state.applyConfig(Config.fromJson(obj.optJSONObject("config")))
                    actions.toast(ctx.getString(R.string.preset_applied))
                    onReuse()
                },
            )
        }
    }

    if (state.history.length() > 0) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            TextButton(
                text = ctx.getString(R.string.history_clear),
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    ConfirmDialog(
        show = confirmClear,
        title = ctx.getString(R.string.history_clear),
        summary = ctx.getString(R.string.history_clear_confirm),
        confirmText = ctx.getString(R.string.confirm),
        dismissText = ctx.getString(R.string.cancel),
        onConfirm = { state.clearHistory() },
        onDismiss = { confirmClear = false },
    )
}

@Composable
private fun HistoryRow(obj: JSONObject, timeText: String, onReuse: () -> Unit) {
    val ctx = LocalContext.current
    val ok = obj.optBoolean("ok", true)
    val cfg = Config.fromJson(obj.optJSONObject("config"))
    val app = obj.optString("app", ctx.getString(R.string.history_unknown_app))
    val error = obj.optString("error", "")

    val summary = buildString {
        append(timeText).append(" · ").append(cfg.summary()).append(" · ")
        append(ctx.getString(if (ok) R.string.history_ok else R.string.history_fail))
        if (error.isNotEmpty()) {
            append('\n').append(ctx.getString(R.string.history_error)).append("：").append(error)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = app, style = MiuixTheme.textStyles.main)
            Text(
                text = summary,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.width(10.dp))
        TextButton(
            text = ctx.getString(R.string.history_reuse),
            onClick = onReuse,
            minWidth = 0.dp,
            minHeight = 34.dp,
            insideMargin = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 14.dp,
                vertical = 5.dp,
            ),
        )
    }
}
