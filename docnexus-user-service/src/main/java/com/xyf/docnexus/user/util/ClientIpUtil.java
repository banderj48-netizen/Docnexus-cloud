package com.xyf.docnexus.user.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 客户端 IP 解析工具。
 *
 * <p>user-service 位于网关后面，直接读取 remoteAddr 往往只能拿到网关地址。
 * 当前项目约定只有 Gateway 可以访问 user-service，因此优先读取 Gateway 清洗后注入的 X-Client-IP。
 * 如果没有该请求头，再兼容读取 X-Forwarded-For / X-Real-IP 等代理头，最后降级到 remoteAddr。</p>
 */
public final class ClientIpUtil {

    private static final String UNKNOWN = "unknown";

    private ClientIpUtil() {
        // 工具类不允许实例化
    }

    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "0.0.0.0";
        }

        String ip = firstValidIp(request.getHeader("X-Client-IP"));
        if (StringUtils.hasText(ip)) {
            return ip;
        }

        ip = firstValidIp(request.getHeader("X-Forwarded-For"));
        if (StringUtils.hasText(ip)) {
            return ip;
        }

        ip = firstValidIp(request.getHeader("X-Real-IP"));
        if (StringUtils.hasText(ip)) {
            return ip;
        }

        ip = firstValidIp(request.getHeader("Proxy-Client-IP"));
        if (StringUtils.hasText(ip)) {
            return ip;
        }

        ip = firstValidIp(request.getHeader("WL-Proxy-Client-IP"));
        if (StringUtils.hasText(ip)) {
            return ip;
        }

        return StringUtils.hasText(request.getRemoteAddr()) ? request.getRemoteAddr() : "0.0.0.0";
    }

    /**
     * X-Forwarded-For 可能是多个 IP，用第一个非 unknown 的 IP 代表真实客户端。
     */
    private static String firstValidIp(String value) {
        if (!StringUtils.hasText(value) || UNKNOWN.equalsIgnoreCase(value.trim())) {
            return null;
        }

        String[] parts = value.split(",");
        for (String part : parts) {
            String ip = part == null ? null : part.trim();
            if (StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip)) {
                return ip;
            }
        }
        return null;
    }
}
