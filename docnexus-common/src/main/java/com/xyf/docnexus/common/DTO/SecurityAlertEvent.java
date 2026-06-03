package com.xyf.docnexus.common.DTO;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安全告警事件兼容 DTO。
 *
 * <p>当前业务代码优先使用 common.event.SecurityAlertEvent；该类保留给历史代码兼容。</p>
 */
@Data
public class SecurityAlertEvent {
    private String eventId;
    private String requestId;
    private String traceId;
    private String alertType;
    private String alertLevel;
    private Long userId;
    private String clientIp;
    private String method;
    private String path;
    private String message;
    private String detailJson;
    private LocalDateTime occurredAt;
}
