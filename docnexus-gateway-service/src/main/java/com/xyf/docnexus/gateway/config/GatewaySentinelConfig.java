package com.xyf.docnexus.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.common.event.SecurityAlertEvent;
import com.xyf.docnexus.gateway.event.GatewayEventPublisher;
import com.xyf.docnexus.gateway.filters.GatewayAuditGlobalFilter;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sentinel Gateway 阻断处理配置。
 *
 * <p>默认只接入 Sentinel，不在代码里硬编码强限流规则；生产可通过 Sentinel Dashboard 或配置中心下发规则。</p>
 */
@Component
public class GatewaySentinelConfig {

    private final GatewaySentinelProperties properties;
    private final GatewayEventPublisher eventPublisher;

    public GatewaySentinelConfig(GatewaySentinelProperties properties, GatewayEventPublisher eventPublisher) {
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 初始化 Sentinel 控制台地址和统一 block 响应。
     */
    @PostConstruct
    public void init() {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return;
        }
        System.setProperty("csp.sentinel.dashboard.server", properties.getDashboard());
        System.setProperty("csp.sentinel.api.port", properties.getTransportPort());
        GatewayCallbackManager.setBlockHandler(blockRequestHandler());
    }

    /**
     * 构造 Sentinel block 响应处理器。
     */
    private BlockRequestHandler blockRequestHandler() {
        return (exchange, throwable) -> {
            publishSentinelBlock(exchange, throwable);
            exchange.getAttributes().put(GatewayAuditGlobalFilter.ATTR_ERROR_MESSAGE, "Sentinel 网关保护触发");
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"code\":429,\"message\":\"服务繁忙，请稍后再试\",\"data\":null}";
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body);
        };
    }

    /**
     * 发送 Sentinel 阻断安全告警事件。
     */
    private void publishSentinelBlock(ServerWebExchange exchange, Throwable throwable) {
        SecurityAlertEvent event = new SecurityAlertEvent();
        event.setEventId(UUID.randomUUID().toString().replace("-", ""));
        event.setRequestId(exchange.getAttribute(GatewayAuditGlobalFilter.ATTR_REQUEST_ID));
        event.setTraceId(exchange.getAttribute(GatewayAuditGlobalFilter.ATTR_TRACE_ID));
        event.setAlertType("SENTINEL_BLOCK");
        event.setAlertLevel("WARN");
        event.setUserId(exchange.getAttribute(GatewayAuditGlobalFilter.ATTR_USER_ID));
        event.setClientIp(exchange.getAttributeOrDefault(GatewayAuditGlobalFilter.ATTR_CLIENT_IP, "0.0.0.0"));
        event.setMethod(exchange.getRequest().getMethod().name());
        event.setPath(exchange.getRequest().getURI().getPath());
        event.setMessage("Sentinel 网关保护触发");
        event.setDetailJson(throwable == null ? null : "{\"reason\":\"" + throwable.getClass().getSimpleName() + "\"}");
        event.setOccurredAt(LocalDateTime.now());
        eventPublisher.publishSecurityAlert(MqTopicConstants.TAG_SENTINEL_BLOCK, event);
    }
}
