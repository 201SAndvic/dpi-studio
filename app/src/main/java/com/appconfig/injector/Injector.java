package com.appconfig.injector;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.lsposed.patch.ApkPatcher;
import org.lsposed.patch.KeystoreSpec;
import org.lsposed.patch.PatchSpec;
import org.lsposed.patch.util.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Security;
import java.util.List;
import java.util.zip.ZipFile;

/** 真正干活的地方：释放模块 → 写配置 → 调 LSPatch → 自检 → 导出。 */
public final class Injector {

    public interface Result {
        void done(File apk, String error);
    }

    private static final String MODULE_ASSET = "module.apk";
    private static final String MODULE_IN_APK = "assets/lspatch/modules/top.jwyihao.appconfig.apk";

    private Injector() {
    }

    public static void start(final Context ctx, final File target, final String targetName,
                             final Config cfg, final Result result) {
        new Thread(() -> {
            File out = null;
            String err = null;
            try {
                out = patch(ctx, target, targetName, cfg);
            } catch (Throwable t) {
                err = String.valueOf(t.getMessage());
                Logs.append("[!] 注入失败：" + t);
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                for (String line : sw.toString().split("\n")) {
                    Logs.append(line);
                }
            }
            final File fo = out;
            final String fe = err;
            new Handler(Looper.getMainLooper()).post(() -> result.done(fo, fe));
        }, "inject").start();
    }

    private static File patch(Context ctx, File target, String targetName, Config cfg) throws Exception {
        // Android 上 /tmp 不可写；LSPatch 内部用 KeyStore.getDefaultType()，内置的是 BKS 密钥库
        System.setProperty("java.io.tmpdir", ctx.getCacheDir().getAbsolutePath());
        Security.setProperty("keystore.type", "BKS");

        File work = new File(ctx.getCacheDir(), "work");
        if (!work.exists() && !work.mkdirs()) {
            throw new IllegalStateException("无法创建缓存目录");
        }

        Logs.append("========== 开始注入 ==========");
        Logs.append("目标：" + targetName);
        Logs.append("参数：" + cfg.summary());
        String xml = cfg.toModuleConfig().toXml();
        for (String line : xml.split("\n")) {
            Logs.append(line);
        }

        // 1) 释放内置模块 APK
        File moduleSrc = new File(work, "module-src.apk");
        try (InputStream in = ctx.getAssets().open(MODULE_ASSET);
             OutputStream os = new FileOutputStream(moduleSrc)) {
            if (in == null) {
                throw new IllegalStateException("内置模块 APK 不存在");
            }
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        }
        Logs.append("模块 APK 已释放：" + moduleSrc.length() + " 字节");

        // 2) 把参数写进模块的 assets/config.xml
        File moduleOut = new File(work, "module-configured.apk");
        ApkRewriter.replaceEntry(moduleSrc, moduleOut, "assets/config.xml",
                xml.getBytes(StandardCharsets.UTF_8));
        Logs.append("已写入配置：" + moduleOut.length() + " 字节");

        // 6) 额外写一份「应用专属」的外部配置。
        //
        // 模块找配置的顺序是：
        //   1. <宿主私有目录>/<包名>.xml   （Android 11+ 其它应用写不进去）
        //   2. <宿主私有目录>/appconfig.xml
        //   3. /sdcard/AppConfig/<包名>.xml   ← 我们写这个
        //   4. /sdcard/AppConfig/appconfig.xml
        // 只要 3 存在，就会盖过 4（也就是以前手工留下的那份旧配置），
        // 保证目标应用读到的一定是这次注入的参数。
        writeExternalConfig(ctx, target, xml);

        // 3) 调用 LSPatch（JingMatrix/LSPatch v1.2）
        File workOut = new File(work, "patch-out");
        if (!workOut.exists() && !workOut.mkdirs()) {
            throw new IllegalStateException("无法创建输出目录");
        }
        File[] olds = workOut.listFiles();
        if (olds != null) {
            for (File f : olds) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        Logs.append("调用 LSPatch（签名绕过 " + cfg.sigBypass + "，注入原始 dex=" + cfg.injectDex + "）…");
        UiLogger logger = new UiLogger();
        PatchSpec spec = PatchSpec.builder()
                .apk(target)
                .outputDir(workOut)
                .module(moduleOut)
                .sigBypassLevel(cfg.sigBypass)
                .injectDex(cfg.injectDex)
                .forceOverwrite(true)
                .verbose(false)
                .keystore(KeystoreSpec.builtIn())
                .build();
        List<File> produced = new ApkPatcher(logger, spec).patch();
        if (produced == null || produced.isEmpty()) {
            throw new IllegalStateException("没有生成成品 APK");
        }
        File raw = produced.get(0);
        if (!raw.isFile() || raw.length() == 0) {
            throw new IllegalStateException("成品 APK 是空的");
        }

        // 4) 结果自检
        String captured = Logs.text();
        if (captured.contains("parse xml failed") || captured.contains("InputStream cannot be null")) {
            //noinspection ResultOfMethodCallIgnored
            raw.delete();
            throw new IllegalStateException("清单解析失败（内置资源不完整），已丢弃成品");
        }
        boolean hasManifest;
        boolean hasModule;
        try (ZipFile zf = new ZipFile(raw)) {
            hasManifest = zf.getEntry("AndroidManifest.xml") != null;
            hasModule = zf.getEntry(MODULE_IN_APK) != null;
        }
        if (!hasManifest || !hasModule) {
            //noinspection ResultOfMethodCallIgnored
            raw.delete();
            throw new IllegalStateException("成品结构不完整（清单或模块缺失），已丢弃");
        }

        // 5) 复制到外部目录方便导出
        File exportDir = ctx.getExternalFilesDir(null);
        if (exportDir == null) {
            exportDir = ctx.getCacheDir();
        }
        File out = new File(exportDir, stripExt(targetName) + "-lspatched.apk");
        try (InputStream in = Files.newInputStream(raw.toPath());
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        }
        Logs.append("[i] 成品 APK：" + out.getAbsolutePath()
                + "（" + (out.length() / 1024 / 1024) + " MB）");
        return out;
    }

    /** 写 /sdcard/AppConfig/<包名>.xml，优先级高于全局的 appconfig.xml。 */
    private static void writeExternalConfig(Context ctx, File targetApk, String xml) {
        String pkg = packageNameOf(ctx, targetApk);
        if (pkg == null || pkg.isEmpty()) {
            Logs.append("[!] 读不出目标包名，跳过外部配置（改用模块内置配置）");
            return;
        }
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "AppConfig");
            if (!dir.exists() && !dir.mkdirs()) {
                Logs.append("[!] 无法创建 " + dir.getAbsolutePath() + "，跳过外部配置");
                return;
            }
            File global = new File(dir, "appconfig.xml");
            if (global.isFile()) {
                Logs.append("[i] 检测到全局外部配置 " + global.getAbsolutePath()
                        + "，已用应用专属配置覆盖它");
            }
            File f = new File(dir, pkg + ".xml");
            try (OutputStream os = new FileOutputStream(f)) {
                os.write(xml.getBytes(StandardCharsets.UTF_8));
            }
            Logs.append("[i] 已写入外部配置：" + f.getAbsolutePath());
            // 模块还会找上一级目录的写法，顺手也放一份
            File alt = new File("/storage/emulated/0/AppConfig", pkg + ".xml");
            if (!alt.equals(f)) {
                try (OutputStream os = new FileOutputStream(alt)) {
                    os.write(xml.getBytes(StandardCharsets.UTF_8));
                }
                Logs.append("[i] 已写入外部配置：" + alt.getAbsolutePath());
            }
        } catch (Throwable t) {
            Logs.append("[!] 写外部配置失败（不影响内置配置）：" + t.getMessage());
        }
    }

    private static String packageNameOf(Context ctx, File apk) {
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            return info == null ? null : info.packageName;
        } catch (Throwable t) {
            return null;
        }
    }

    public static String stripExt(String name) {
        int i = name == null ? -1 : name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : String.valueOf(name);
    }

    private static final class UiLogger extends Logger {
        @Override
        public void d(String s) {
            if (verbose) {
                Logs.append("[D] " + s);
            }
        }

        @Override
        public void i(String s) {
            Logs.append("[i] " + s);
        }

        @Override
        public void e(String s) {
            Logs.append("[!] " + s);
        }
    }
}
