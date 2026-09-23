package com.appconfig.injector;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 采集设备与屏幕信息。 */
public final class DeviceInfo {

    public static final class Item {
        public final String label;
        public final String value;

        Item(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    public static final class DisplayInfo {
        public final String title;
        public final int displayId;
        public final List<Item> items = new ArrayList<>();

        DisplayInfo(String title, int displayId) {
            this.title = title;
            this.displayId = displayId;
        }
    }

    private DeviceInfo() {
    }

    public static List<Item> device(Context ctx, String appVersion, int recordCount) {
        List<Item> out = new ArrayList<>();
        out.add(new Item(ctx.getString(R.string.label_android), Build.VERSION.RELEASE));
        out.add(new Item(ctx.getString(R.string.label_sdk), String.valueOf(Build.VERSION.SDK_INT)));
        out.add(new Item(ctx.getString(R.string.label_model), Build.MANUFACTURER + " " + Build.MODEL));
        out.add(new Item(ctx.getString(R.string.label_brand), Build.BRAND));
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "-";
        out.add(new Item(ctx.getString(R.string.label_abi), abi));
        out.add(new Item(ctx.getString(R.string.label_app_version), appVersion));
        return out;
    }

    private static int displayCount(Context ctx) {
        try {
            DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
            Display[] ds = dm == null ? null : dm.getDisplays();
            if (ds == null) {
                return 0;
            }
            int n = 0;
            for (Display d : ds) {
                if (d != null && d.isValid() && !isOverlay(d)) {
                    n++;
                }
            }
            return n;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean isOverlay(Display d) {
        try {
            String name = d.getName();
            return name != null && name.startsWith("Overlay");
        } catch (Throwable t) {
            return false;
        }
    }

    public static List<DisplayInfo> displays(Context ctx, int minWidthDp) {
        List<DisplayInfo> out = new ArrayList<>();
        Display[] ds;
        try {
            DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
            ds = dm == null ? null : dm.getDisplays();
        } catch (Throwable t) {
            return out;
        }
        if (ds == null) {
            return out;
        }
        int index = 0;
        for (Display d : ds) {
            if (d == null || !d.isValid() || isOverlay(d)) {
                continue;
            }
            DisplayMetrics m = new DisplayMetrics();
            try {
                d.getRealMetrics(m);
            } catch (Throwable t) {
                try {
                    d.getMetrics(m);
                } catch (Throwable ignored) {
                    continue;
                }
            }
            if (m.widthPixels <= 0 || m.heightPixels <= 0) {
                continue;
            }
            int displayId = displayIdOf(d, index);
            DisplayInfo info = new DisplayInfo(title(ctx, d, index), displayId);
            info.items.add(new Item(ctx.getString(R.string.display_resolution),
                    m.widthPixels + " × " + m.heightPixels));
            info.items.add(new Item(ctx.getString(R.string.display_density),
                    m.densityDpi + " dpi"));
            info.items.add(new Item(ctx.getString(R.string.display_inches), inches(m)));
            if (minWidthDp > 0) {
                int h = Math.round((float) minWidthDp * m.heightPixels / m.widthPixels);
                info.items.add(new Item(ctx.getString(R.string.display_dp),
                        minWidthDp + " × " + h + " dp"));
            }
            out.add(info);
            index++;
        }
        return out;
    }

    private static String title(Context ctx, Display d, int index) {
        int id = displayIdOf(d, index);
        if (id == Display.DEFAULT_DISPLAY) {
            return ctx.getString(R.string.display_primary);
        }
        if (index == 1) {
            return ctx.getString(R.string.display_secondary);
        }
        return ctx.getString(R.string.display_n, index + 1);
    }

    private static int displayIdOf(Display d, int index) {
        int id;
        try {
            id = d.getDisplayId();
        } catch (Throwable t) {
            id = index;
        }
        return id;
    }

    private static String inches(DisplayMetrics m) {
        try {
            if (m.xdpi <= 0 || m.ydpi <= 0) {
                return "-";
            }
            double w = m.widthPixels / (double) m.xdpi;
            double h = m.heightPixels / (double) m.ydpi;
            double d = Math.sqrt(w * w + h * h);
            if (d <= 0 || d > 100) {
                return "-";
            }
            return String.format(Locale.US, "%.1f 英寸", d);
        } catch (Throwable t) {
            return "-";
        }
    }
}
