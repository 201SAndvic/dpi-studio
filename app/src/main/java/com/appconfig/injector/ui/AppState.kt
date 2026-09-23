package com.appconfig.injector.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.appconfig.injector.Config
import com.appconfig.injector.Logs
import com.appconfig.injector.Prefs
import org.json.JSONArray
import java.io.File

/**
 * 界面状态容器。配置、预设、历史全部落在 SharedPreferences 里，
 * 这里只做一层 Compose 可观察的包装。
 */
class AppState(private val ctx: Context) {

    var config by mutableStateOf(Prefs.loadConfig(ctx))
        private set

    var targetName by mutableStateOf("")
        private set

    var targetFile by mutableStateOf<File?>(null)
        private set

    var lastOutput by mutableStateOf<File?>(null)
        private set

    var history by mutableStateOf(Prefs.history(ctx))
        private set

    var presets by mutableStateOf(Prefs.presets(ctx))
        private set

    /** 本应用自己的界面宽度（dp）。0 表示跟随系统。 */
    var selfMinWidth by androidx.compose.runtime.mutableIntStateOf(Prefs.selfMinWidth(ctx))
        private set

    var onboarded by mutableStateOf(Prefs.onboarded(ctx))
        private set

    fun applySelfMinWidth(v: Int) {
        Prefs.setSelfMinWidth(ctx, v)
        selfMinWidth = v
    }

    fun finishOnboarding() {
        Prefs.setOnboarded(ctx)
        onboarded = true
    }

    // ---------------------------------------------------------------- 配置

    fun update(block: (Config) -> Unit) {
        val c = config.copy()
        block(c)
        config = c
        Prefs.saveConfig(ctx, c)
    }

    fun applyConfig(c: Config) {
        config = c.copy()
        Prefs.saveConfig(ctx, config)
    }

    // ---------------------------------------------------------------- 目标文件

    fun setTarget(file: File?, name: String) {
        targetFile = file
        targetName = if (file == null) "" else name
    }

    fun setOutput(file: File?) {
        lastOutput = file
    }

    // ---------------------------------------------------------------- 列表

    fun reloadHistory() {
        history = Prefs.history(ctx)
    }

    fun reloadPresets() {
        presets = Prefs.presets(ctx)
    }

    fun addHistoryEntry(app: String, ok: Boolean, cfg: Config, out: File?) {
        val o = org.json.JSONObject()
        try {
            o.put("app", app.ifEmpty { "未知应用" })
            o.put("time", System.currentTimeMillis())
            o.put("ok", ok)
            o.put("config", cfg.toJson())
            if (out != null) o.put("out", out.absolutePath)
        } catch (_: Exception) {
            // 忽略
        }
        Prefs.addHistory(ctx, o)
        reloadHistory()
    }

    fun clearHistory() {
        Prefs.clearHistory(ctx)
        reloadHistory()
    }

    fun savePreset(name: String) {
        Prefs.addPreset(ctx, name, config)
        reloadPresets()
    }

    fun removePreset(name: String) {
        Prefs.removePreset(ctx, name)
        reloadPresets()
    }

    fun reloadPresetsIfNeeded(): JSONArray = presets

    // ---------------------------------------------------------------- 日志

    fun logText(): String = Logs.text()

    fun clearLog() = Logs.clear()
}
