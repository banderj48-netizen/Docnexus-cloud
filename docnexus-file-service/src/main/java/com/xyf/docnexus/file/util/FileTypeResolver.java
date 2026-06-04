package com.xyf.docnexus.file.util;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 文件类型解析工具。
 */
public final class FileTypeResolver {

    private FileTypeResolver() {
    }

    /**
     * 解析文件扩展名。
     */
    public static String extension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 解析文件大类。
     */
    public static String category(String fileName) {
        return switch (extension(fileName)) {
            case "pdf" -> "PDF";
            case "doc", "docx", "wps", "wpt", "wpd" -> "WORD";
            case "ppt", "pptx", "dps", "dpt" -> "PPT";
            case "txt" -> "TXT";
            default -> "UNKNOWN";
        };
    }

    /**
     * 解析前端表格短类型。
     */
    public static String shortType(String fileName) {
        return switch (category(fileName)) {
            case "WORD" -> "W";
            case "PPT" -> "P";
            case "TXT" -> "TXT";
            case "PDF" -> "PDF";
            default -> "FILE";
        };
    }
}
