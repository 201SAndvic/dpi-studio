package com.appconfig.injector.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.ScreenCapture
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.appconfig.injector.Prefs
import com.appconfig.injector.R

@Composable
fun SettingsScreen(
    state: AppState,
    actions: AppActions,
    onAbout: () -> Unit,
    onEditSelfSize: () -> Unit,
) {
    val ctx = LocalContext.current
    var showLogMenu by remember { mutableStateOf(false) }
    var showLogView by remember { mutableStateOf(false) }
    var showBackupMenu by remember { mutableStateOf(false) }
    var confirmClearLog by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }

    AppCard {
        ClickableRow(
            title = ctx.getString(R.string.settings_self_size),
            summary = ctx.getString(R.string.settings_self_size_desc),
            value = if (state.selfMinWidth > 0) {
                ctx.getString(R.string.settings_self_size_value, state.selfMinWidth)
            } else {
                ctx.getString(R.string.settings_self_size_follow)
            },
            startIcon = MiuixIcons.Medium.ScreenCapture,
            onClick = onEditSelfSize,
        )
        ClickableRow(
            title = ctx.getString(R.string.settings_snapshot),
            summary = ctx.getString(R.string.settings_snapshot_desc),
            startIcon = MiuixIcons.Medium.UploadCloud,
            onClick = actions.snapshot,
        )
    }

    AppCard {
        ClickableRow(
            title = ctx.getString(R.string.settings_logs),
            summary = ctx.getString(R.string.settings_logs_desc),
            startIcon = MiuixIcons.Medium.Notes,
            onClick = { showLogMenu = true },
        )
        ClickableRow(
            title = ctx.getString(R.string.settings_backup),
            summary = ctx.getString(R.string.settings_backup_desc),
            startIcon = MiuixIcons.Medium.Backup,
            onClick = { showBackupMenu = true },
        )
        ClickableRow(
            title = ctx.getString(R.string.settings_about),
            summary = ctx.getString(R.string.settings_about_desc),
            startIcon = MiuixIcons.Medium.Info,
            onClick = onAbout,
        )
    }

    // ------------------------------------------------------------ 日志菜单
    OverlayDialog(
        show = showLogMenu,
        title = ctx.getString(R.string.settings_logs),
        onDismissRequest = { showLogMenu = false },
    ) {
        Column(Modifier.fillMaxWidth()) {
            DialogOption(ctx.getString(R.string.settings_log_view)) {
                showLogMenu = false
                showLogView = true
            }
            DialogOption(ctx.getString(R.string.settings_log_copy)) {
                showLogMenu = false
                copyLogToClipboard(ctx, reportHeader(ctx, appVersionName(ctx)) + state.logText())
                actions.toast(ctx.getString(R.string.settings_log_copied))
            }
            DialogOption(ctx.getString(R.string.settings_log_save)) {
                showLogMenu = false
                actions.exportLog()
            }
            DialogOption(ctx.getString(R.string.settings_log_clear)) {
                showLogMenu = false
                confirmClearLog = true
            }
        }
    }

    // ------------------------------------------------------------ 日志内容
    if (showLogView) {
        val log = state.logText()
        OverlayDialog(
            show = showLogView,
            title = ctx.getString(R.string.settings_logs),
            onDismissRequest = { showLogView = false },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = log.trim().ifEmpty { ctx.getString(R.string.settings_log_empty) },
                    style = MiuixTheme.textStyles.body2.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
            ) {
                TextButton(
                    text = ctx.getString(R.string.settings_log_copy),
                    onClick = {
                        copyLogToClipboard(ctx, reportHeader(ctx, appVersionName(ctx)) + log)
                        actions.toast(ctx.getString(R.string.settings_log_copied))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                TextButton(
                    text = ctx.getString(R.string.close),
                    onClick = { showLogView = false },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    }

    // ------------------------------------------------------------ 备份菜单
    OverlayDialog(
        show = showBackupMenu,
        title = ctx.getString(R.string.settings_backup),
        onDismissRequest = { showBackupMenu = false },
    ) {
        Column(Modifier.fillMaxWidth()) {
            DialogOption(ctx.getString(R.string.backup_export)) {
                showBackupMenu = false
                actions.exportBackup()
            }
            DialogOption(ctx.getString(R.string.backup_import)) {
                showBackupMenu = false
                confirmImport = true
            }
        }
    }

    ConfirmDialog(
        show = confirmClearLog,
        title = ctx.getString(R.string.settings_log_clear),
        summary = ctx.getString(R.string.settings_log_clear_confirm),
        confirmText = ctx.getString(R.string.confirm),
        dismissText = ctx.getString(R.string.cancel),
        onConfirm = {
            state.clearLog()
            actions.toast(ctx.getString(R.string.settings_log_cleared))
        },
        onDismiss = { confirmClearLog = false },
    )

    ConfirmDialog(
        show = confirmImport,
        title = ctx.getString(R.string.backup_import),
        summary = ctx.getString(R.string.backup_confirm_import),
        confirmText = ctx.getString(R.string.confirm),
        dismissText = ctx.getString(R.string.cancel),
        onConfirm = { actions.importBackup() },
        onDismiss = { confirmImport = false },
    )
}

@Composable
private fun DialogOption(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MiuixTheme.textStyles.main)
    }
}
