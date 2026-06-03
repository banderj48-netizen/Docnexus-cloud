package com.xyf.docnexus.gateway.filters;

import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.common.event.SecurityAlertEvent;
import com.xyf.docnexus.gateway.config.GatewayRedisRateLimitProperties;
import com.xyf.docnexus.gateway.event.GatewayEventPublisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Redis 分布式限流过滤器。
 *
 * <p>固定窗口计数通过 Lua 原子完成 INCR 和 EXPIRE，保证多 Gateway 实例共享同一限流结果。</p>
 */
@Component
public class RedisRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final GatewayRedisRateLimitProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayEventPublisher eventPublisher;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RedisRateLimitGlobalFilter(GatewayRedisRateLimitProperties properties,
                                      ReactiveStringRedisTemplate redisTemplate,
                                      GatewayEventPublisher eventPublisher) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 对匹配规则的请求执行 Redis 限流。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return chain.filter(exchange);
        }

        GatewayRedisRateLimitProperties.Rule rule = matchRule(exchange.getRequest().getURI().getPath());
        if (rule == null) {
            return chain.filter(exchange);
        }

        String identity = resolveIdentity(exchange, rule.getKeyType());
        String redisKey = buildRedisKey(rule, identity);
        return redisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(redisKey), List.of(String.valueOf(rule.getWindowSeconds())))
                .next()
                .flatMap(current -> {
                    if (current != null && current > rule.getLimit()) {
                        publishRateLimited(exchange, rule, current);
                        return writeTooManyRequests(exchange);
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(exception -> {
                    if (Boolean.TRUE.equals(properties.getFailOpen())) {
                        return chain.filter(exchange);
                    }
                    exchange.getAttributes().put(GatewayAuditGlobalFilter.ATTR_ERROR_MESSAGE, "Redis 限流组件异常");
                    return writeTooManyRequests(exchange);
                });
    }

    /**
     * 在鉴权后执行，保证 USER 维度规则可以读取网关注入的 X-User-Id。
     */
    @Override
    public int getOrder() {
        return 10;
    }

    /**
     * 按配置顺序匹配第一条可用限流规则。
     */
    private GatewayRedisRateLimitProperties.Rule matchRule(String path) {
        for (Map.Entry<String, GatewayRedisRateLimitProperties.Rule> entry : properties.getRules().entrySet()) {
            GatewayRedisRateLimitProperties.Rule rule = entry.getValue();
            if (Boolean.TRUE.equals(rule.getEnabled()) && pathMatcher.match(rule.getPathPattern(), path)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * 根据规则维度计算限流身份。
     */
    private String resolveIdentity(ServerWebExchange exchange, String keyType) {
        String clientIp = exchange.getAttributeOrDefault(GatewayAuditGlobalFilter.ATTR_CLIENT_IP, "0.0.0.0");
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if ("IP".equalsIgnoreCase(keyType)) {
            return clientIp;
        }
        if ("USER".equalsIgnoreCase(keyType)) {
            return userId == null || userId.isBlank() ? clientIp : userId;
        }
        return userId == null || userId.isBlank() ? clientIp : userId;
    }

    /**
     * 构建带窗口编号的 Redis 限流 Key。
     */
    private String buildRedisKey(GatewayRedisRateLimitProperties.Rule rule, String identity) {
        long window = System.currentTimeMillis() / 1000L / Math.max(1L, rule.getWindowSeconds());
        return "rate:gateway:" + rule.getPathPattern() + ":" + rule.getKeyType() + ":" + identity + ":" + window;
    }

    /**
     * 发送限流安全告警事件。
     */
    private void publishRateLimited(ServerWebExchange exchange, GatewayRedisRateLimitProperties.Rule rule, Long current) {
        SecurityAlertEvent event = new SecurityAlertEvent();
        event.setEventId(UUID.randomUUID().toString().replace("-", ""));
        event.setRequestId(exchange.getAttribute(GatewayAuditGlobalFilter.ATTR_REQUEST_ID));
        event.setTraceId(exchange.getAttribute(GatewayAuditGlobalFilter.ATTR_TRACE_ID));
        event.setAlertType("RATE_LIMITED");
        event.setAlertLevel("WARN");
        event.setUserId(exchange.getAttribute(GatewayAuditGlobalFilter.ATTR_USER_ID));
        event.setClientIp(exchange.getAttributeOrDefault(GatewayAuditGlobalFilter.ATTR_CLIENT_IP, "0.0.0.0"));
        event.setMethod(exchange.getRequest().getMethod().name());
        event.setPath(exchange.getRequest().getURI().getPath());
        event.setMessage("Redis 分布式限流触发");
        event.setDetailJson("{\"limit\":" + rule.getLimit() + ",\"current\":" + current + "}");
        event.setOccurredAt(LocalDateTime.now());
        eventPublisher.publishSecurityAlert(MqTopicConstants.TAG_RATE_LIMITED, event);
    }

    /**
     * 返回统一 429 响应。
     */
    private Mono<Void> writeTooManyRequests(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}";
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
