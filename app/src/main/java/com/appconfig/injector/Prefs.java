package com.appconfig.injector;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** SharedPreferences 读写：当前参数、历史记录、自定义预设、默认值。 */
public final class Prefs {

    private static final String NAME = "appconfig";

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    // ---- 当前参数 ----

    public static void saveConfig(Context c, Config cfg) {
        sp(c).edit().putString("config", cfg.toJson().toString()).apply();
    }

    public static Config loadConfig(Context c) {
        String s = sp(c).getString("config", null);
        if (s == null) {
            return new Config();
        }
        try {
            return Config.fromJson(new JSONObject(s));
        } catch (Exception e) {
            return new Config();
        }
    }

    // ---- 默认值 ----

    public static int defaultSig(Context c) {
        return sp(c).getInt("defaultSig", 2);
    }

    public static void setDefaultSig(Context c, int v) {
        sp(c).edit().putInt("defaultSig", v).apply();
    }

    public static boolean defaultInjectDex(Context c) {
        return sp(c).getBoolean("defaultInjectDex", false);
    }

    /** v4.0 起默认「只改尺寸」，这里一次性把老版本留下的圆屏适配关掉。 */
    public static boolean migratedOnlySize(Context c) {
        return sp(c).getBoolean("migratedOnlySize", false);
    }

    public static void setMigratedOnlySize(Context c) {
        sp(c).edit().putBoolean("migratedOnlySize", true).apply();
    }

    public static void setDefaultInjectDex(Context c, boolean v) {
        sp(c).edit().putBoolean("defaultInjectDex", v).apply();
    }

    // ---- 历史记录 ----

    public static JSONArray history(Context c) {
        try {
            return new JSONArray(sp(c).getString("history", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static void addHistory(Context c, JSONObject entry) {
        JSONArray arr = history(c);
        JSONArray out = new JSONArray();
        out.put(entry);
        for (int i = 0; i < arr.length() && i < 29; i++) {
            try {
                out.put(arr.get(i));
            } catch (Exception ignored) {
                // 忽略坏数据
            }
        }
        sp(c).edit().putString("history", out.toString()).apply();
    }

    public static void clearHistory(Context c) {
        sp(c).edit().putString("history", "[]").apply();
    }

    // ---- 备份 / 恢复 ----

    public static String exportJson(Context c) {
        JSONObject o = new JSONObject();
        try {
            o.put("version", 1);
            o.put("config", loadConfig(c).toJson());
            o.put("presets", presets(c));
            o.put("history", history(c));
        } catch (Exception ignored) {
            // 忽略
        }
        return o.toString();
    }

    public static boolean importJson(Context c, String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONObject cfg = o.optJSONObject("config");
            if (cfg != null) {
                saveConfig(c, Config.fromJson(cfg));
            }
            org.json.JSONArray presets = o.optJSONArray("presets");
            if (presets != null) {
                sp(c).edit().putString("presets", presets.toString()).apply();
            }
            org.json.JSONArray history = o.optJSONArray("history");
            if (history != null) {
                sp(c).edit().putString("history", history.toString()).apply();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- 自定义预设 ----

    public static JSONArray presets(Context c) {
        try {
            return new JSONArray(sp(c).getString("presets", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static void addPreset(Context c, String name, Config cfg) {
        JSONArray arr = presets(c);
        JSONArray out = new JSONArray();
        JSONObject o = new JSONObject();
        try {
            o.put("name", name);
            o.put("config", cfg.toJson());
        } catch (Exception ignored) {
            // 忽略
        }
        out.put(o);
        for (int i = 0; i < arr.length() && i < 19; i++) {
            try {
                JSONObject old = arr.getJSONObject(i);
                if (name.equals(old.optString("name"))) {
                    continue;
                }
                out.put(old);
            } catch (Exception ignored) {
                // 忽略
            }
        }
        sp(c).edit().putString("presets", out.toString()).apply();
    }

    public static void removePreset(Context c, String name) {
        JSONArray arr = presets(c);
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                if (!name.equals(o.optString("name"))) {
                    out.put(o);
                }
            } catch (Exception ignored) {
                // 忽略
            }
        }
        sp(c).edit().putString("presets", out.toString()).apply();
    }

    // ---- 本机显示尺寸 ----
    // 车机常见 160dpi：屏幕 1440px 宽会被当成 1440dp，界面小得没法看。
    // 这里存一个「本应用自己按多少 dp 宽显示」，0 表示跟随系统。

    public static int selfMinWidth(Context c) {
        return sp(c).getInt("selfMinWidth", 0);
    }

    public static void setSelfMinWidth(Context c, int v) {
        sp(c).edit().putInt("selfMinWidth", v).apply();
    }

    public static boolean onboarded(Context c) {
        return sp(c).getBoolean("onboarded", false);
    }

    public static void setOnboarded(Context c) {
        sp(c).edit().putBoolean("onboarded", true).apply();
    }

    // ---- 屏幕自定义名称 ----

    /** 用户给某块屏幕起的名字；没改过返回 null。 */
    public static String displayName(Context c, int displayId) {
        String s = sp(c).getString("displayName_" + displayId, null);
        return (s == null || s.trim().isEmpty()) ? null : s;
    }

    /** 传 null 或空串表示恢复默认名称。 */
    public static void setDisplayName(Context c, int displayId, String name) {
        if (name == null || name.trim().isEmpty()) {
            sp(c).edit().remove("displayName_" + displayId).apply();
        } else {
            sp(c).edit().putString("displayName_" + displayId, name.trim()).apply();
        }
    }
}
