package com.appconfig.injector;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** 把模块 APK 里的 assets/config.xml 换成新的内容。 */
public final class ApkRewriter {

    private ApkRewriter() {
    }

    public static void replaceEntry(File in, File out, String entryName, byte[] content) throws IOException {
        if (out.exists() && !out.delete()) {
            throw new IOException("无法覆盖输出文件：" + out);
        }
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (ZipFile zf = new ZipFile(in, StandardCharsets.UTF_8);
             OutputStream fos = new BufferedOutputStream(Files.newOutputStream(out.toPath()));
             ZipOutputStream zo = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {
            Set<String> seen = new HashSet<>();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                seen.add(e.getName());
                byte[] data;
                try (InputStream is = zf.getInputStream(e)) {
                    data = readAll(is);
                }
                boolean replaced = e.getName().equals(entryName);
                if (replaced) {
                    data = content;
                }
                ZipEntry ne = new ZipEntry(e.getName());
                if (e.getTime() >= 0) {
                    ne.setTime(e.getTime());
                }
                // 被替换的这一项（assets/config.xml）必须写成「已存储」，
                // 把大小和 CRC 直接写进局部头。
                //
                // 模块是用 ZipInputStream 读这份配置的，它拿 zipEntry.size 去 new byte[]：
                // 如果写成 DEFLATED，Java 的 ZipOutputStream 会改用「数据描述符」，
                // 局部头里 size 是 -1，模块就会 new byte[-1] 抛异常 → 读配置失败 →
                // 退回代码内置默认值（forceRound=true、roundSize=1.0、roundRatio=1.0），
                // 界面会被裁成屏幕的 1/√2，也就是「只剩中间」。
                boolean sizeMustBeKnown =
                        replaced || (!e.isDirectory() && e.getMethod() == ZipEntry.STORED);
                if (!e.isDirectory() && sizeMustBeKnown) {
                    ne.setMethod(ZipEntry.STORED);
                    ne.setSize(data.length);
                    ne.setCompressedSize(data.length);
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    ne.setCrc(crc.getValue());
                } else {
                    ne.setMethod(ZipEntry.DEFLATED);
                }
                zo.putNextEntry(ne);
                if (!e.isDirectory()) {
                    zo.write(data);
                }
                zo.closeEntry();
            }
            if (!seen.contains(entryName)) {
                ZipEntry ne = new ZipEntry(entryName);
                ne.setMethod(ZipEntry.DEFLATED);
                zo.putNextEntry(ne);
                zo.write(content);
                zo.closeEntry();
            }
        }
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}
