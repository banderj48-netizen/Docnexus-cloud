package com.xyf.docnexus.common.DTO;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Gateway 请求审计事件兼容 DTO。
 *
 * <p>当前业务代码优先使用 common.event.GatewayAuditEvent；该类保留给历史代码兼容。Gateway 审计日志不记录耗时。</p>
 */
@Data
public class GatewayAuditEvent {
    private String eventId;
    private String requestId;
    private String traceId;
    private Long userId;
    private String username;
    private String clientIp;
    private String method;
    private String path;
    private String routeId;
    private String targetService;
    private String requestKind;
    private Integer statusCode;
    private String userAgent;
    private String errorMessage;
    private LocalDateTime occurredAt;
}
