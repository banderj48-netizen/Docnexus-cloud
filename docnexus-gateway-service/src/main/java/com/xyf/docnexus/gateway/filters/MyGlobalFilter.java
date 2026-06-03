package com.xyf.docnexus.gateway.filters;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.common.event.SecurityAlertEvent;
import com.xyf.docnexus.gateway.config.GatewayAuthCacheProperties;
import com.xyf.docnexus.gateway.config.GatewayJwtProperties;
import com.xyf.docnexus.gateway.event.GatewayEventPublisher;
import com.xyf.docnexus.gateway.security.GatewayAuthCache;
import com.xyf.docnexus.gateway.security.GatewayTrustedHeaderSigner;
import com.xyf.docnexus.gateway.security.ReactiveRedisTokenSessionStore;
import com.xyf.docnexus.gateway.util.JwtVerifyTool;
import com.xyf.docnexus.gateway.util.JwtVerifyTool.UserTokenPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 网关全局鉴权过滤器。
 *
 * <p>职责：白名单放行、accessToken 验签、Redis 实时态校验、Caffeine 短缓存、可信身份头注入和鉴权失败告警。</p>
 */
@Component
public class MyGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(MyGlobalFilter.class);

    private final GatewayJwtProperties jwtProperties;
    private final GatewayAuthCacheProperties authCacheProperties;
    private final JwtVerifyTool jwtVerifyTool;
    private final ReactiveRedisTokenSessionStore redisTokenSessionStore;
    private final GatewayAuthCache gatewayAuthCache;
    private final GatewayTrustedHeaderSigner trustedHeaderSigner;
    private final GatewayEventPublisher eventPublisher;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public MyGlobalFilter(GatewayJwtProperties jwtProperties,
                          GatewayAuthCacheProperties authCacheProperties,
                          JwtVerifyTool jwtVerifyTool,
                          ReactiveRedisTokenSessionStore redisTokenSessionStore,
                          GatewayAuthCache gatewayAuthCache,
                          GatewayTrustedHeaderSigner trustedHeaderSigner,
                          GatewayEventPublisher eventPublisher) {
        this.jwtProperties = jwtProperties;
        this.authCacheProperties = authCacheProperties;
        this.jwtVerifyTool = jwtVerifyTool;
        this.redisTokenSessionStore = redisTokenSessionStore;
        this.gatewayAuthCache = gatewayAuthCache;
        this.trustedHeaderSigner = trustedHeaderSigner;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 执行网关鉴权和可信请求头注入。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String clientIp = resolveClientIp(request);
        String requestId = ensureRequestId(exchange);
        String traceId = ensureTraceId(exchange, requestId);
        exchange.getAttributes().put(GatewayAuditGlobalFilter.ATTR_CLIENT_IP, clientIp);

        if (isWhitePath(path)) {
            ServerHttpRequest trustedRequest = buildTrustedClientIpRequest(request, clientIp, requestId, traceId);
            return chain.filter(exchange.mutate().request(trustedRequest).build());
        }

        String accessToken;
        try {
            accessToken = jwtVerifyTool.extractBearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        } catch (IllegalArgumentException exception) {
            publishSecurityAlert(exchange, "UNAUTHORIZED", "缺少或非法 Authorization 请求头", exception.getMessage());
            return writeUnauthorizedResponse(exchange.getResponse(), "登录状态已失效，请重新登录");
        }

        boolean strictPath = isStrictPath(path);
        String tokenHash = gatewayAuthCache.hashToken(accessToken, clientIp);
        if (!strictPath) {
            UserTokenPayload cachedPayload = gatewayAuthCache.getIfValid(tokenHash);
            if (cachedPayload != null) {
                writeUserAttributes(exchange, cachedPayload);
                ServerHttpRequest trustedRequest = buildTrustedUserRequest(request, cachedPayload, requestId, traceId);
                return chain.filter(exchange.mutate().request(trustedRequest).build());
            }
        }

        UserTokenPayload payload;
        try {
            payload = jwtVerifyTool.verifyToken(accessToken);
        } catch (JWTVerificationException | IllegalArgumentException exception) {
            publishSecurityAlert(exchange, "INVALID_TOKEN", "JWT 验签或声明校验失败", exception.getMessage());
            return writeUnauthorizedResponse(exchange.getResponse(), "登录状态已失效，请重新登录");
        }

        return redisTokenSessionStore.isTokenActive(payload, clientIp)
                .flatMap(active -> {
                    if (!active) {
                        writeUserAttributes(exchange, payload);
                        publishSecurityAlert(exchange, "SESSION_REVOKED", "Redis 实时登录态校验失败",
                                "blacklist/session/tokenVersion 校验未通过");
                        return writeUnauthorizedResponse(exchange.getResponse(), "登录状态已失效，请重新登录");
                    }

                    if (!strictPath) {
                        gatewayAuthCache.put(tokenHash, payload);
                    }

                    writeUserAttributes(exchange, payload);
                    ServerHttpRequest trustedRequest = buildTrustedUserRequest(request, payload, requestId, traceId);
                    log.debug("网关鉴权通过，path={}, userId={}, role={}", path, payload.userId(), payload.role());
                    return chain.filter(exchange.mutate().request(trustedRequest).build());
                });
    }

    /**
     * 鉴权过滤器在审计之后、限流之前执行。
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 判断当前请求路径是否在白名单中。
     */
    private boolean isWhitePath(String path) {
        return jwtProperties.getWhiteList()
                .stream()
                .anyMatch(pattern -> antPathMatcher.match(pattern, path));
    }

    /**
     * 判断当前路径是否需要绕过 Caffeine 短缓存，直接执行强校验。
     */
    private boolean isStrictPath(String path) {
        return authCacheProperties.getStrictPaths()
                .stream()
                .anyMatch(pattern -> antPathMatcher.match(pattern, path));
    }

    /**
     * 构建携带可信用户身份的下游请求。
     */
    private ServerHttpRequest buildTrustedUserRequest(ServerHttpRequest request,
                                                      UserTokenPayload payload,
                                                      String requestId,
                                                      String traceId) {
        String clientIp = resolveClientIp(request);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = trustedHeaderSigner.sign(requestId, timestamp, clientIp,
                String.valueOf(payload.userId()), payload.jwtId());

        return request.mutate()
                .headers(this::removeClientForgedHeaders)
                .header("X-User-Id", String.valueOf(payload.userId()))
                .header("X-Username", payload.username() == null ? "" : payload.username())
                .header("X-User-Role", payload.role() == null ? "" : payload.role())
                .header("X-Access-Jti", payload.jwtId() == null ? "" : payload.jwtId())
                .header("X-Client-IP", clientIp)
                .header("X-Request-Id", requestId)
                .header("X-Trace-Id", traceId)
                .header("X-Gateway-Timestamp", timestamp)
                .header("X-Gateway-Signature", signature)
                .build();
    }

    /**
     * 为白名单请求注入可信客户端 IP 和网关签名。
     */
    private ServerHttpRequest buildTrustedClientIpRequest(ServerHttpRequest request,
                                                          String clientIp,
                                                          String requestId,
                                                          String traceId) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = trustedHeaderSigner.sign(requestId, timestamp, clientIp, "", "");
        return request.mutate()
                .headers(this::removeClientForgedHeaders)
                .header("X-Client-IP", clientIp)
                .header("X-Request-Id", requestId)
                .header("X-Trace-Id", traceId)
                .header("X-Gateway-Timestamp", timestamp)
                .header("X-Gateway-Signature", signature)
                .build();
    }

    /**
     * 删除客户端可能伪造的身份头和网关内部头。
     */
    private void removeClientForgedHeaders(HttpHeaders headers) {
        headers.remove("X-User-Id");
        headers.remove("X-Username");
        headers.remove("X-User-Role");
        headers.remove("X-Access-Jti");
        headers.remove("X-Client-IP");
        headers.remove("X-Request-Id");
        headers.remove("X-Trace-Id");
        headers.remove("X-Gateway-Timestamp");
        headers.remove("X-Gateway-Signature");
    }

    /**
     * 写入审计过滤器可读取的用户上下文。
     */
    private void writeUserAttributes(ServerWebExchange exchange, UserTokenPayload payload) {
        exchange.getAttributes().put(GatewayAuditGlobalFilter.ATTR_USER_ID, payload.userId());
        exchange.getAttributes().put(GatewayAuditGlobalFilter.ATTR_USERNAME, payload.username());
    }

    /**
     * 获取请求 ID；如果审计过滤器尚未写入，则兜底生成。
     */
    private String ensureRequestId(ServerWebExchange exchange) {
        String requestId = exchange.getAttribute(GatewayAuditGlobalFilter.ATTR_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
            exchange.getAttributes().put(GatewayAuditGlobalFilter.ATTR_REQUEST_ID, requestId);
        }
        return requestId;
    }

    /**
     * 解析全链路追踪 ID，缺失时复用 requestId。
     */
    private String ensureTraceId(ServerWebExchange exchange, String requestId) {
        String traceId = exchange.getAttribute(GatewayAuditGlobalFilter.ATTR_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = requestId;
            exchange.getAttributes().put(GatewayAuditGlobalFilter.ATTR_TRACE_ID, traceId);
        }
        return traceId;
    }

    /**
     * 解析当前请求真实 IP。
     */
    private String resolveClientIp(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "0.0.0.0";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    /**
     * 发布鉴权失败安全告警。
     */
    private void publishSecurityAlert(ServerWebExchange exchange, String alertType, String message, String detail) {
        exchange.getAttributes().put(GatewayAuditGlobalFilter.ATTR_ERROR_MESSAGE, message);
        SecurityAlertEvent event = new SecurityAlertEvent();
        event.setEventId(UUID.randomUUID().toString().replace("-", ""));
        event.setRequestId(exchange.getAttribute(GatewayAuditGlobalFilter.ATTR_REQUEST_ID));
        event.setTraceId(exchange.getAttribute(GatewayAuditGlobalFilter.ATTR_TRACE_ID));
        event.setAlertType(alertType);
        event.setAlertLevel("WARN");
        event.setUserId(exchange.getAttribute(GatewayAuditGlobalFilter.ATTR_USER_ID));
        event.setClientIp(exchange.getAttributeOrDefault(GatewayAuditGlobalFilter.ATTR_CLIENT_IP, "0.0.0.0"));
        event.setMethod(exchange.getRequest().getMethod().name());
        event.setPath(exchange.getRequest().getURI().getPath());
        event.setMessage(message);
        event.setDetailJson(detail == null ? null : "{\"reason\":\"" + detail.replace("\"", "'") + "\"}");
        event.setOccurredAt(LocalDateTime.now());
        eventPublisher.publishSecurityAlert(MqTopicConstants.TAG_SECURITY_ALERT, event);
    }

    /**
     * 返回统一 401 JSON 响应。
     */
    private Mono<Void> writeUnauthorizedResponse(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
