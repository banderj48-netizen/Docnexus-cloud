package com.xyf.docnexus.common.event;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户服务业务域事件。
 *
 * <p>用于注册、修改密码、修改资料、Token 版本变化等异步用户模块事件，后续可被通知服务、审计服务或初始化任务消费。</p>
 */
public class UserBusinessEvent implements Serializable {

    private String eventId;
    private String traceId;
    private Long userId;
    private String username;
    private String eventType;
    private String businessKey;
    private String message;
    private LocalDateTime occurredAt;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
