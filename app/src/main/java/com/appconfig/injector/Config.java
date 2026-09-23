package com.appconfig.injector;

import org.json.JSONObject;

/** 一次注入用到的全部参数。 */
public final class Config {

    public String minWidth = "640";
    public String roundSize = "1.432";
    public String roundRatio = "1.24";
    public String hOffset = "0";
    public String vOffset = "-1";
    public String bgColor = "";
    public String bgAlpha = "-1";
    public String toast = "never";
    public boolean fakeAppList = true;
    // 默认只改界面尺寸，不做圆屏适配：双屏比例不同时都可用
    public boolean round = false;
    public boolean forceRound = false;
    public boolean injectDex = false;
    public int sigBypass = 2;

    public Config copy() {
        Config c = new Config();
        c.minWidth = minWidth;
        c.roundSize = roundSize;
        c.roundRatio = roundRatio;
        c.hOffset = hOffset;
        c.vOffset = vOffset;
        c.bgColor = bgColor;
        c.bgAlpha = bgAlpha;
        c.toast = toast;
        c.fakeAppList = fakeAppList;
        c.round = round;
        c.forceRound = forceRound;
        c.injectDex = injectDex;
        c.sigBypass = sigBypass;
        return c;
    }

    public ModuleConfig toModuleConfig() {
        ModuleConfig c = new ModuleConfig();
        c.put("minWidth", minWidth);
        c.putBool("fakeAppList", fakeAppList);
        c.putBool("round", round);
        c.putBool("forceRound", forceRound);
        c.put("roundSize", roundSize);
        c.put("roundRatio", roundRatio);
        c.put("horizontalOffset", hOffset);
        c.put("verticalOffset", vOffset);
        c.put("backgroundColor", bgColor);
        c.put("backgroundAlpha", bgAlpha);
        c.put("toast", toast);
        return c;
    }

    public String summary() {
        boolean pad = round || forceRound;
        return "最小宽度 " + minWidth
                + (pad ? " · 圆屏适配" : " · 仅改尺寸")
                + " · 签名绕过 " + sigBypass
                + (injectDex ? " · 原始 dex" : "");
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("minWidth", minWidth);
            o.put("roundSize", roundSize);
            o.put("roundRatio", roundRatio);
            o.put("hOffset", hOffset);
            o.put("vOffset", vOffset);
            o.put("bgColor", bgColor);
            o.put("bgAlpha", bgAlpha);
            o.put("toast", toast);
            o.put("fakeAppList", fakeAppList);
            o.put("round", round);
            o.put("forceRound", forceRound);
            o.put("injectDex", injectDex);
            o.put("sigBypass", sigBypass);
        } catch (Exception ignored) {
            // JSONObject.put 不会失败
        }
        return o;
    }

    public static Config fromJson(JSONObject o) {
        Config c = new Config();
        if (o == null) {
            return c;
        }
        c.minWidth = o.optString("minWidth", c.minWidth);
        c.roundSize = o.optString("roundSize", c.roundSize);
        c.roundRatio = o.optString("roundRatio", c.roundRatio);
        c.hOffset = o.optString("hOffset", c.hOffset);
        c.vOffset = o.optString("vOffset", c.vOffset);
        c.bgColor = o.optString("bgColor", c.bgColor);
        c.bgAlpha = o.optString("bgAlpha", c.bgAlpha);
        c.toast = o.optString("toast", c.toast);
        c.fakeAppList = o.optBoolean("fakeAppList", c.fakeAppList);
        c.round = o.optBoolean("round", c.round);
        c.forceRound = o.optBoolean("forceRound", c.forceRound);
        c.injectDex = o.optBoolean("injectDex", c.injectDex);
        c.sigBypass = o.optInt("sigBypass", c.sigBypass);
        return c;
    }

    /** 数量校验，返回错误信息或 null。 */
    public String validate() {
        try {
            int v = Integer.parseInt(minWidth.trim());
            if (v < 100 || v > 3000) {
                return "最小宽度建议在 100~3000 之间";
            }
        } catch (Exception e) {
            return "最小宽度必须是整数";
        }
        String[] floats = {roundSize, roundRatio, hOffset, vOffset};
        String[] names = {"容器大小", "容器比例", "水平偏移", "垂直偏移"};
        for (int i = 0; i < floats.length; i++) {
            try {
                Double.parseDouble(floats[i].trim());
            } catch (Exception e) {
                return names[i] + "必须是数字";
            }
        }
        try {
            Integer.parseInt(bgAlpha.trim());
        } catch (Exception e) {
            return "背景透明度必须是整数（-1 表示不设置）";
        }
        return null;
    }
}
