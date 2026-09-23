package com.appconfig.injector;

import java.util.LinkedHashMap;
import java.util.Map;

/** 生成 app_config 模块用的 assets/config.xml。 */
public final class ModuleConfig {

    public ModuleConfig() {
    }

    public static final String[] ITEM_ORDER = {
            "minWidth", "fakeAppList", "round", "forceRound", "roundSize", "roundRatio",
            "horizontalOffset", "verticalOffset", "backgroundColor", "backgroundAlpha", "toast"
    };

    private final Map<String, String> items = new LinkedHashMap<>();
    private String author = "DPI 工坊";

    public ModuleConfig author(String a) {
        this.author = a;
        return this;
    }

    public ModuleConfig put(String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            items.put(key, value.trim());
        }
        return this;
    }

    public ModuleConfig putBool(String key, boolean value) {
        items.put(key, Boolean.toString(value));
        return this;
    }

    public String toXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n");
        sb.append("<!-- 由「DPI 工坊」生成 -->\n");
        sb.append("<config author=\"").append(esc(author)).append("\">\n");
        sb.append("    <!-- 默认配置，作用于所有设备和应用 -->\n");
        sb.append("    <rule-set>\n");
        for (String key : ITEM_ORDER) {
            String v = items.get(key);
            if (v == null) {
                continue;
            }
            sb.append("        <item name=\"").append(esc(key)).append("\">")
                    .append(esc(v)).append("</item>\n");
        }
        sb.append("    </rule-set>\n");
        sb.append("</config>\n");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
