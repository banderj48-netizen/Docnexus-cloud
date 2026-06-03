package com.xyf.docnexus.file.util;

import java.io.InputStream;
import java.security.MessageDigest;

/**
 * 文件哈希工具。
 */
public final class HashUtils {

    private HashUtils() {
    }

    /**
     * 计算输入流 SHA-256。
     */
    public static String sha256(InputStream inputStream) {
        try (InputStream stream = inputStream) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("计算文件 SHA-256 失败", exception);
        }
    }

    /**
     * 字节数组转十六进制字符串。
     */
    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
