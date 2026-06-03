package com.xyf.docnexus.gateway.filters;

import com.xyf.docnexus.common.event.GatewayAuditEvent;
import com.xyf.docnexus.gateway.event.GatewayEventPublisher;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Gateway 请求审计过滤器。
 *
 * <p>该过滤器包住完整网关链路，即使鉴权失败、限流短路或 Sentinel 阻断，也会在响应结束后发送审计事件。</p>
 */
@Component
public class GatewayAuditGlobalFilter implements GlobalFilter, Ordered {

    public static final String ATTR_REQUEST_ID = "docnexus.requestId";
    public static final String ATTR_TRACE_ID = "docnexus.traceId";
    public static final String ATTR_USER_ID = "docnexus.userId";
    public static final String ATTR_USERNAME = "docnexus.username";
    public static final String ATTR_CLIENT_IP = "docnexus.clientIp";
    public static final String ATTR_ERROR_MESSAGE = "docnexus.errorMessage";

    private final GatewayEventPublisher eventPublisher;

    public GatewayAuditGlobalFilter(GatewayEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 记录请求开始时间，并在响应结束后发送审计事件。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        LocalDateTime occurredAt = LocalDateTime.now();
        String requestId = resolveRequestId(exchange.getRequest());
        String traceId = resolveTraceId(exchange.getRequest(), requestId);
        String clientIp = resolveClientIp(exchange.getRequest());
        exchange.getAttributes().put(ATTR_REQUEST_ID, requestId);
        exchange.getAttributes().put(ATTR_TRACE_ID, traceId);
        exchange.getAttributes().put(ATTR_CLIENT_IP, clientIp);

        ServerHttpRequest requestWithId = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-Request-Id");
                    headers.remove("X-Trace-Id");
                })
                .header("X-Request-Id", requestId)
                .header("X-Trace-Id", traceId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(requestWithId).build();
        return chain.filter(mutatedExchange)
                .doFinally(signalType -> publishAudit(mutatedExchange, occurredAt, requestId, traceId, clientIp));
    }

    /**
     * 过滤器优先级保持较高，确保失败和短路请求也能被审计。
     */
    @Override
    public int getOrder() {
        return -200;
    }

    /**
     * 构造并发送脱敏审计事件。
     */
    private void publishAudit(ServerWebExchange exchange,
                              LocalDateTime occurredAt,
                              String requestId,
                              String traceId,
                              String clientIp) {
        GatewayAuditEvent event = new GatewayAuditEvent();
        event.setEventId(UUID.randomUUID().toString().replace("-", ""));
        event.setRequestId(requestId);
        event.setTraceId(traceId);
        event.setUserId(exchange.getAttribute(ATTR_USER_ID));
        event.setUsername(exchange.getAttribute(ATTR_USERNAME));
        event.setClientIp(clientIp);
        event.setMethod(exchange.getRequest().getMethod().name());
        event.setPath(exchange.getRequest().getURI().getPath());
        event.setRouteId(resolveRouteId(exchange));
        event.setTargetService(resolveTargetService(exchange));
        event.setRequestKind(resolveRequestKind(exchange.getRequest()));
        event.setStatusCode(exchange.getResponse().getStatusCode() == null
                ? null
                : exchange.getResponse().getStatusCode().value());
        event.setUserAgent(exchange.getRequest().getHeaders().getFirst("User-Agent"));
        event.setErrorMessage(exchange.getAttribute(ATTR_ERROR_MESSAGE));
        event.setOccurredAt(occurredAt);
        eventPublisher.publishAudit(event);
    }

    /**
     * 解析或生成请求 ID。
     */
    private String resolveRequestId(ServerHttpRequest request) {
        String requestId = request.getHeaders().getFirst("X-Request-Id");
        return requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "")
                : requestId;
    }

    /**
     * 解析或生成全链路追踪 ID；没有传入时复用 requestId，保证所有日志表都能关联同一次请求。
     */
    private String resolveTraceId(ServerHttpRequest request, String requestId) {
        String traceId = request.getHeaders().getFirst("X-Trace-Id");
        return traceId == null || traceId.isBlank() ? requestId : traceId;
    }

    /**
     * 解析 Gateway 看到的客户端 IP。
     */
    private String resolveClientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "0.0.0.0";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    /**
     * 从 Gateway 路由上下文解析 routeId。
     */
    private String resolveRouteId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route == null ? null : route.getId();
    }

    /**
     * 从 Gateway 路由上下文解析目标服务。
     */
    private String resolveTargetService(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route == null || route.getUri() == null ? null : route.getUri().toString();
    }

    /**
     * 按接口语义粗分请求类型，供管理员后续区分自动查询和用户动作。
     */
    private String resolveRequestKind(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();
        if (path.startsWith("/api/auth/")) {
            return "AUTH";
        }
        if (path.contains("/heartbeat") || path.contains("/status/") || path.contains("/recoverable")) {
            return "SYSTEM_POLLING";
        }
        if (HttpMethod.GET.equals(method)) {
            return "AUTO_QUERY";
        }
        String normalizedPath = path.toLowerCase(Locale.ROOT);
        if (normalizedPath.contains("/list") || normalizedPath.contains("/summary")) {
            return "AUTO_QUERY";
        }
        return "USER_OPERATION";
    }
}
