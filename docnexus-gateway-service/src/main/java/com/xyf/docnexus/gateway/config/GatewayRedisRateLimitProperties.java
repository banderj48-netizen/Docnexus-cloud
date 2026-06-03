package com.xyf.docnexus.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gateway Redis 分布式限流配置。
 *
 * <p>Sentinel 负责网关实例和路由级保护，Redis 负责多网关实例之间共享的精确计数限流。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "docnexus.gateway.redis-rate-limit")
public class GatewayRedisRateLimitProperties {

    /**
     * 是否启用 Redis 分布式限流，默认关闭，避免开发环境误限流。
     */
    private Boolean enabled = false;

    /**
     * Redis 异常时是否放行。生产高安全场景可改为 false。
     */
    private Boolean failOpen = true;

    /**
     * 限流规则集合，按配置顺序匹配，先匹配先使用。
     */
    private Map<String, Rule> rules = new LinkedHashMap<>();

    @Data
    public static class Rule {

        /**
         * 单条规则是否启用。
         */
        private Boolean enabled = true;

        /**
         * Ant 风格路径，例如 /api/files/**。
         */
        private String pathPattern;

        /**
         * 限流维度：IP / USER / USER_OR_IP。
         */
        private String keyType = "USER_OR_IP";

        /**
         * 窗口期内允许的最大请求数。
         */
        private Long limit = 300L;

        /**
         * 固定窗口长度，单位秒。
         */
        private Long windowSeconds = 60L;
    }
}
