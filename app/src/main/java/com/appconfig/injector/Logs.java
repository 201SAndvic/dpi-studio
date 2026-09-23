package com.appconfig.injector;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** 全局日志：内存 + 落盘，供「设置 → 日志」查看和一键复制。 */
public final class Logs {

    public interface Listener {
        void onLogChanged();
    }

    private static final int MAX_CHARS = 200_000;
    private static final long FLUSH_INTERVAL_MS = 400;
    private static final StringBuilder BUF = new StringBuilder();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static File file;
    private static long lastFlush;

    private Logs() {
    }

    public static void init(Context ctx) {
        file = new File(ctx.getFilesDir(), "last-session.log");
        try {
            if (file.isFile()) {
                BUF.append(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // 读不到就算了
        }
    }

    public static void addListener(Listener l) {
        if (!LISTENERS.contains(l)) {
            LISTENERS.add(l);
        }
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    public static synchronized void append(String line) {
        BUF.append(line).append('\n');
        if (BUF.length() > MAX_CHARS) {
            BUF.delete(0, BUF.length() - MAX_CHARS);
            BUF.insert(0, "...(前面的日志已省略)...\n");
        }
        long now = System.currentTimeMillis();
        if (now - lastFlush > FLUSH_INTERVAL_MS) {
            lastFlush = now;
            flush();
        }
        notifyChanged();
    }

    public static synchronized String text() {
        return BUF.toString();
    }

    public static void clear() {
        synchronized (Logs.class) {
            BUF.setLength(0);
            lastFlush = 0;
            flush();
        }
        notifyChanged();
    }

    public static File file() {
        return file;
    }

    private static void flush() {
        if (file == null) {
            return;
        }
        final String snapshot = BUF.toString();
        final File f = file;
        new Thread(() -> {
            try {
                Files.write(f.toPath(), snapshot.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                // 落盘失败不影响使用
            }
        }, "log-flush").start();
    }

    private static void notifyChanged() {
        MAIN.post(() -> {
            for (Listener l : new ArrayList<>(LISTENERS)) {
                l.onLogChanged();
            }
        });
    }
}
