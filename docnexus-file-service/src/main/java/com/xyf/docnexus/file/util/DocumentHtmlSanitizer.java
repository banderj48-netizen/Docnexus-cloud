package com.xyf.docnexus.file.util;

/**
 * 文档编辑 HTML 清理工具。
 */
public final class DocumentHtmlSanitizer {

    private DocumentHtmlSanitizer() {
    }

    /**
     * 清理危险标签、事件属性、外链资源和样式属性。
     */
    public static String sanitize(String html) {
        String safe = html == null ? "" : html;
        safe = safe.replaceAll("(?is)<script.*?>.*?</script>", "");
        safe = safe.replaceAll("(?is)<iframe.*?>.*?</iframe>", "");
        safe = safe.replaceAll("(?is)<object.*?>.*?</object>", "");
        safe = safe.replaceAll("(?is)<embed.*?>.*?</embed>", "");
        safe = safe.replaceAll("(?is)<img\\s+[^>]*>", "");
        safe = safe.replaceAll("(?i)\\s+on\\w+\\s*=\\s*\"[^\"]*\"", "");
        safe = safe.replaceAll("(?i)\\s+on\\w+\\s*=\\s*'[^']*'", "");
        safe = safe.replaceAll("(?i)\\s+style\\s*=\\s*\"[^\"]*\"", "");
        safe = safe.replaceAll("(?i)\\s+style\\s*=\\s*'[^']*'", "");
        safe = safe.replaceAll("(?i)\\s+src\\s*=\\s*\"[^\"]*\"", "");
        safe = safe.replaceAll("(?i)\\s+src\\s*=\\s*'[^']*'", "");
        safe = safe.replaceAll("(?i)\\s+href\\s*=\\s*\"\\s*(javascript|data|http|https):[^\"]*\"", "");
        safe = safe.replaceAll("(?i)\\s+href\\s*=\\s*'\\s*(javascript|data|http|https):[^']*'", "");
        return safe;
    }
}
