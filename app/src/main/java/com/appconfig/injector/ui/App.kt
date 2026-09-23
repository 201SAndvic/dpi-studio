package com.appconfig.injector.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.appconfig.injector.Prefs
import com.appconfig.injector.R
import com.appconfig.injector.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** 界面外部动作（选文件、存文件、提示），由 App 层注入给各个页面。 */
/** 顶层路由。depth 只用来决定页面切换动画的方向。 */
private enum class Route(val depth: Int) {
    Main(0),
    About(1),
    SelfSize(1),
    Picker(1),
    SavePicker(1),
    ImportPicker(1),
    Onboarding(0),
}

class AppActions(
    val pickTarget: () -> Unit,
    val saveToDownloads: () -> Unit,
    val pickSaveFolder: () -> Unit,
    val exportXml: () -> Unit,
    val exportLog: () -> Unit,
    val exportBackup: () -> Unit,
    val importBackup: () -> Unit,
    val snapshot: () -> Unit,
    val toast: (String) -> Unit,
)

@Composable
fun DpiStudioApp(activity: ComponentActivity) {
    val state = remember { AppState(activity) }
    val ctx = activity
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showSavePicker by rememberSaveable { mutableStateOf(false) }
    var showImportPicker by rememberSaveable { mutableStateOf(false) }
    var showSelfSize by rememberSaveable { mutableStateOf(false) }
    var showOnboarding by rememberSaveable { mutableStateOf(!state.onboarded) }
    var draftWidth by rememberSaveable {
        mutableIntStateOf(if (state.selfMinWidth > 0) state.selfMinWidth else suggestSelfMinWidth(ctx))
    }

    val toast: (String) -> Unit = { msg ->
        Messages.show(msg)
    }

    // 返回键 / 侧滑返回：先关二级页面，再回主页，最后才退出
    BackHandler {
        when {
            showOnboarding -> ctx.finish()
            showSavePicker -> showSavePicker = false
            showImportPicker -> showImportPicker = false
            showSelfSize -> showSelfSize = false
            showAbout -> showAbout = false
            showPicker -> showPicker = false
            tab != 0 -> tab = 0
            else -> ctx.finish()
        }
    }

    // 引导页里「跟随系统」时仍然按推荐值渲染，否则引导本身就小到没法点
    val previewWidth = if (showOnboarding) {
        if (draftWidth > 0) draftWidth else suggestSelfMinWidth(ctx)
    } else {
        state.selfMinWidth
    }

    // ---------------------------------------------------------------- 环境快照

    val runSnapshot: () -> Unit = {
        toast(ctx.getString(R.string.snapshot_working))
        scope.launch {
            val bytes = EnvSnapshot.build(ctx, activity, state)
            val name = EnvSnapshot.fileName()
            val saved = withContext(Dispatchers.IO) {
                EnvSnapshot.saveToDownloads(ctx, name, bytes, "application/zip")
            }
            toast(
                if (saved != null) {
                    ctx.getString(R.string.snapshot_saved, saved)
                } else {
                    ctx.getString(R.string.snapshot_failed, "无法写入下载目录")
                }
            )
        }
    }

    val writePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) runSnapshot()
        else toast(ctx.getString(R.string.snapshot_need_permission))
    }

    val onSnapshot: () -> Unit = {
        val needPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needPermission) {
            writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            runSnapshot()
        }
    }

    // ---------------------------------------------------------------- 文件选择 / 保存

    // 系统选择器只作为兜底：车机上 DocumentsUI 常常是坏的
    val systemPickTarget = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) readTargetApk(ctx, uri, state, toast)
    }

    // 导出统一写系统下载目录，不再弹系统选择器（车机上 DocumentsUI 是坏的）
    val exportToDownloads: (String, ByteArray, String) -> Unit = { name, bytes, mime ->
        Thread({
            val saved = saveBytesToDownloads(ctx, name, bytes, mime)
            ctx.mainExecutor.execute {
                toast(
                    if (saved != null) ctx.getString(R.string.save_done, saved)
                    else ctx.getString(R.string.save_failed, name)
                )
            }
        }, "export").start()
    }

    // 保存成品：默认写下载目录，也可以自己挑文件夹
    val saveOutputToDownloads: () -> Unit = {
        val out = state.lastOutput
        if (out == null) {
            toast("没有可保存的成品")
        } else {
            Thread({
                val saved = saveFileToDownloads(
                    ctx,
                    out.name,
                    out,
                    "application/vnd.android.package-archive",
                )
                ctx.mainExecutor.execute {
                    toast(
                        if (saved != null) ctx.getString(R.string.save_done, saved)
                        else ctx.getString(R.string.save_failed, out.name)
                    )
                }
            }, "save-apk").start()
        }
    }

    val saveOutputToFolder: (File) -> Unit = { dir ->
        val out = state.lastOutput
        showSavePicker = false
        if (out == null) {
            toast("没有可保存的成品")
        } else {
            Thread({
                val saved = copyIntoDir(out, dir)
                ctx.mainExecutor.execute {
                    toast(
                        if (saved != null) ctx.getString(R.string.save_done, saved.absolutePath)
                        else ctx.getString(R.string.save_failed, dir.absolutePath)
                    )
                }
            }, "save-apk").start()
        }
    }

    val importBackupFrom: (File) -> Unit = { file ->
        showImportPicker = false
        Thread({
            val text = try {
                openInput(ctx, file).use { String(it.readBytes(), StandardCharsets.UTF_8) }
            } catch (_: Exception) {
                null
            }
            ctx.mainExecutor.execute {
                if (text != null && Prefs.importJson(ctx, text)) {
                    state.applyConfig(Prefs.loadConfig(ctx))
                    state.reloadPresets()
                    state.reloadHistory()
                    toast(ctx.getString(R.string.backup_imported))
                } else {
                    toast(ctx.getString(R.string.backup_failed))
                }
            }
        }, "import-backup").start()
    }

    val actions = remember {
        AppActions(
            pickTarget = { showPicker = true },
            saveToDownloads = saveOutputToDownloads,
            pickSaveFolder = { showSavePicker = true },
            exportXml = {
                exportToDownloads(
                    "config.xml",
                    state.config.toModuleConfig().toXml().toByteArray(StandardCharsets.UTF_8),
                    "text/xml",
                )
            },
            exportLog = {
                exportToDownloads(
                    "dpi-studio-log.txt",
                    (reportHeader(ctx, appVersionName(ctx)) + state.logText())
                        .toByteArray(StandardCharsets.UTF_8),
                    "text/plain",
                )
            },
            exportBackup = {
                exportToDownloads(
                    "dpi-studio-backup.json",
                    Prefs.exportJson(ctx).toByteArray(StandardCharsets.UTF_8),
                    "application/json",
                )
            },
            importBackup = { showImportPicker = true },
            snapshot = onSnapshot,
            toast = toast,
        )
    }

    // ---------------------------------------------------------------- 界面

    val route = when {
        showOnboarding -> Route.Onboarding
        showPicker -> Route.Picker
        showSavePicker -> Route.SavePicker
        showImportPicker -> Route.ImportPicker
        showSelfSize -> Route.SelfSize
        showAbout -> Route.About
        else -> Route.Main
    }

    WithSelfSize(previewWidth) {
        Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                if (initialState == Route.Onboarding || targetState == Route.Onboarding) {
                    // 首次引导不做滑动，淡入淡出即可
                    fadeIn(tween(240)) togetherWith fadeOut(tween(200))
                } else {
                    // depth 变大 = 进入二级页面，从右侧推入；返回时反向
                    val forward = targetState.depth >= initialState.depth
                    val dir = if (forward) 1 else -1
                    (
                        slideInHorizontally(tween(320)) { w -> dir * w } + fadeIn(tween(320))
                        ) togetherWith
                        (
                            slideOutHorizontally(tween(320)) { w -> -dir * w / 4 } +
                                fadeOut(tween(220))
                            )
                }
            },
            label = "route",
        ) { currentRoute ->
            when (currentRoute) {
            Route.Onboarding -> OnboardingScreen(
                value = draftWidth,
                suggested = suggestMinWidthSafely(ctx),
                onValueChange = { draftWidth = it },
                onDone = {
                    state.applySelfMinWidth(draftWidth)
                    state.finishOnboarding()
                    showOnboarding = false
                },
            )

            Route.Picker -> FilePickerScreen(
                mode = PickerMode.Apk,
                onPickFile = { file ->
                    showPicker = false
                    useTargetApk(ctx, file, state, toast)
                },
                onPickFolder = {},
                onCancel = { showPicker = false },
                onUseSystemPicker = { systemPickTarget.launch(arrayOf("*/*")) },
            )

            Route.SavePicker -> FilePickerScreen(
                mode = PickerMode.Folder,
                onPickFile = {},
                onPickFolder = saveOutputToFolder,
                onCancel = { showSavePicker = false },
                onUseSystemPicker = {},
            )

            Route.ImportPicker -> FilePickerScreen(
                mode = PickerMode.AnyFile,
                onPickFile = importBackupFrom,
                onPickFolder = {},
                onCancel = { showImportPicker = false },
                onUseSystemPicker = {},
            )

            Route.SelfSize -> SelfSizeScreen(
                value = state.selfMinWidth,
                suggested = suggestSelfMinWidth(ctx),
                onValueChange = { state.applySelfMinWidth(it) },
                onBack = { showSelfSize = false },
            )

            Route.About -> AboutScreen(
                appVersion = appVersionName(ctx),
                selfMinWidth = state.selfMinWidth,
                onBack = { showAbout = false },
                onGithub = { openUrl(ctx, ctx.getString(R.string.repo_url)) },
            )

            Route.Main -> {
                val titles = listOf(
                    ctx.getString(R.string.title_home),
                    ctx.getString(R.string.title_inject),
                    ctx.getString(R.string.title_history),
                    ctx.getString(R.string.title_settings),
                )
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = MiuixIcons.Regular.Home,
                                label = ctx.getString(R.string.nav_home),
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = MiuixIcons.Regular.Tune,
                                label = ctx.getString(R.string.nav_inject),
                            )
                            NavigationBarItem(
                                selected = tab == 2,
                                onClick = { tab = 2 },
                                icon = MiuixIcons.Regular.Recent,
                                label = ctx.getString(R.string.nav_history),
                            )
                            NavigationBarItem(
                                selected = tab == 3,
                                onClick = { tab = 3 },
                                icon = MiuixIcons.Regular.Settings,
                                label = ctx.getString(R.string.nav_settings),
                            )
                        }
                    },
                    containerColor = MiuixTheme.colorScheme.surface,
                ) { inner ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inner)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp),
                    ) {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                val forward = targetState > initialState
                                val dir = if (forward) 1 else -1
                                (
                                    slideInHorizontally(tween(280)) { w -> dir * w / 5 } +
                                        fadeIn(tween(280))
                                    ) togetherWith
                                    (
                                        slideOutHorizontally(tween(220)) { w -> -dir * w / 5 } +
                                            fadeOut(tween(160))
                                        )
                            },
                            label = "tab-content",
                        ) { currentTab ->
                            Column(Modifier.fillMaxWidth()) {
                                PageTitle(titles[currentTab])

                                when (currentTab) {
                                    0 -> HomeScreen(state, actions)
                                    1 -> InjectScreen(state, actions)
                                    2 -> HistoryScreen(state, actions) { tab = 1 }
                                    else -> SettingsScreen(
                                        state = state,
                                        actions = actions,
                                        onAbout = { showAbout = true },
                                        onEditSelfSize = { showSelfSize = true },
                                    )
            }
        }
                }
            }
        }
        }
        }
        }
            // 提示条画在最上层
            MessageOverlay()
        }
    }
}

private fun suggestMinWidthSafely(ctx: Context): Int = suggestSelfMinWidth(ctx)

// ---------------------------------------------------------------- 文件工具

/** 内置文件浏览器选中的 APK：复制到缓存、校验、设为目标。 */
private fun useTargetApk(ctx: Context, src: File, state: AppState, toast: (String) -> Unit) {
    toast("正在读取 APK…")
    Thread({
        var error: String? = null
        var dst: File? = null
        try {
            if (!src.isFile) throw IllegalStateException("文件不存在或无法读取：${src.absolutePath}")
            val dir = File(ctx.cacheDir, "work")
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建缓存目录")
            val f = File(dir, "target-${System.currentTimeMillis()}.apk")
            dst = f
            openInput(ctx, src).use { input ->
                FileOutputStream(f).use { os -> input.copyTo(os, 1 shl 16) }
            }
            ZipFile(f).use { zf ->
                if (zf.getEntry("AndroidManifest.xml") == null) {
                    throw IllegalStateException("这不是一个完整的 APK 文件")
                }
            }
        } catch (e: Exception) {
            val raw = e.message ?: e.toString()
            error = if (raw.contains("EACCES") || raw.contains("Permission denied")) {
                Logs.append("[!] 无法读取目标 APK：${src.absolutePath}（${src.length()} 字节）")
                Logs.append("[!] $raw")
                ctx.getString(R.string.apk_read_denied, src.length() / 1024 / 1024)
            } else {
                raw
            }
        }
        val err = error
        if (err != null) dst?.delete()
        val finalDst = if (err == null) dst else null
        ctx.mainExecutor.execute {
            if (err != null) {
                toast(err)
            } else {
                state.setTarget(finalDst, src.name)
                toast("已载入：${src.name}")
            }
        }
    }, "read-apk-file").start()
}

/**
 * 打开待注入 APK 的输入流。
 *
 * 优先直接读文件；某些车机即使给了「所有文件访问」，路径直读仍会被拒绝
 * （EACCES），这时改从 MediaStore 取同一条目的 URI 再读一次。
 */
private fun openInput(ctx: Context, src: File): InputStream {
    return try {
        src.inputStream()
    } catch (direct: Exception) {
        val uri = mediaStoreUriOf(ctx, src) ?: throw direct
        ctx.contentResolver.openInputStream(uri) ?: throw direct
    }
}

private fun mediaStoreUriOf(ctx: Context, file: File): Uri? = try {
    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    ctx.contentResolver.query(
        collection,
        arrayOf(MediaStore.Files.FileColumns._ID),
        MediaStore.Files.FileColumns.DATA + " = ?",
        arrayOf(file.absolutePath),
        null,
    )?.use { c ->
        if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null
    }
} catch (_: Throwable) {
    null
}

private fun readTargetApk(ctx: Context, uri: Uri, state: AppState, toast: (String) -> Unit) {
    toast("正在读取 APK…")
    Thread({
        var error: String? = null
        var name = "target.apk"
        var dst: File? = null
        try {
            queryName(ctx, uri)?.takeIf { it.isNotEmpty() }?.let { name = it }
            val dir = File(ctx.cacheDir, "work")
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建缓存目录")
            val f = File(dir, "target-${System.currentTimeMillis()}.apk")
            dst = f
            ctx.contentResolver.openInputStream(uri).use { input ->
                if (input == null) throw IllegalStateException("无法读取所选文件")
                FileOutputStream(f).use { os -> input.copyTo(os, 1 shl 16) }
            }
            ZipFile(f).use { zf ->
                if (zf.getEntry("AndroidManifest.xml") == null) {
                    throw IllegalStateException(
                        "这不是单个完整的 APK（像是 .apks/.xapk 拆分包或普通压缩包）。" +
                            "LSPatch 只能注入单个 APK，请换一个 .apk 文件。"
                    )
                }
            }
        } catch (ze: ZipException) {
            error = "这个文件不是有效的 APK：${ze.message}"
        } catch (e: Exception) {
            error = e.message ?: e.toString()
        }
        val err = error
        if (err != null) dst?.delete()
        val finalName = name
        val finalDst = if (err == null) dst else null
        ctx.mainExecutor.execute {
            if (err != null) {
                toast(err)
            } else {
                state.setTarget(finalDst, finalName)
                toast("已载入：$finalName")
            }
        }
    }, "read-apk").start()
}

private fun queryName(ctx: Context, uri: Uri): String? {
    return try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    } catch (_: Exception) {
        null
    }
}

private fun copyToUri(ctx: Context, src: File, dst: Uri, toast: (String) -> Unit) {
    if (!src.isFile) {
        toast("文件不存在")
        return
    }
    toast("正在保存…")
    Thread({
        var msg = "已保存：${src.name}"
        try {
            ctx.contentResolver.openOutputStream(dst).use { out ->
                if (out == null) throw IllegalStateException("无法写入所选位置")
                src.inputStream().use { input -> input.copyTo(out, 1 shl 16) }
            }
        } catch (e: Exception) {
            msg = "保存失败：${e.message}"
        }
        ctx.mainExecutor.execute { toast(msg) }
    }, "save-apk").start()
}

private fun writeToUri(
    ctx: Context,
    dst: Uri,
    data: ByteArray,
    toast: (String) -> Unit,
    onDone: () -> Unit,
) {
    Thread({
        var ok = true
        var msg = ""
        try {
            ctx.contentResolver.openOutputStream(dst).use { out ->
                if (out == null) throw IllegalStateException("无法写入所选位置")
                out.write(data)
            }
        } catch (e: Exception) {
            ok = false
            msg = "导出失败：${e.message}"
        }
        ctx.mainExecutor.execute {
            if (ok) onDone() else toast(msg)
        }
    }, "save-file").start()
}

private fun readTextFromUri(ctx: Context, uri: Uri, toast: (String) -> Unit, onText: (String) -> Unit) {
    Thread({
        val text = try {
            ctx.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                String(input.readBytes(), StandardCharsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
        ctx.mainExecutor.execute {
            if (text != null) onText(text) else toast("文件读取失败")
        }
    }, "read-file").start()
}

fun copyLogToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("dpi-studio-log", text))
}

fun reportHeader(ctx: Context, version: String): String = buildString {
    append("=== DPI 工坊 v").append(version).append(" ===\n")
    append("Android ").append(Build.VERSION.RELEASE)
    append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
    append("设备：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
    append("ABI：").append(Build.SUPPORTED_ABIS.firstOrNull() ?: "?").append('\n')
    append("内置引擎：JingMatrix/LSPatch v1.2\n")
    append("--- 日志 ---\n")
}
