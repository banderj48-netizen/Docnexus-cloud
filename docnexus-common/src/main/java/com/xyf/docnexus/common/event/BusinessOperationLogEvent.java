package com.xyf.docnexus.common.event;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一业务操作日志事件。
 *
 * <p>该事件由业务服务 AOP 或 MQ 消费入口生成，投递到 docnexus_log_event 后由 log-service 统一落库。</p>
 */
public class BusinessOperationLogEvent implements Serializable {

    private String eventId;
    private String requestId;
    private String traceId;
    private Long userId;
    private String username;
    private String sourceService;
    private String module;
    private String functionName;
    private String operationType;
    private String operationName;
    private String triggerType;
    private String operationSource;
    private Boolean userVisible;
    private String businessKey;
    private Boolean success;
    private String alertMessage;
    private String requestHeadersJson;
    private String method;
    private String path;
    private String clientIp;
    private String userAgent;
    private LocalDateTime occurredAt;
    private LocalDateTime completedAt;
    private Long durationMs;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getSourceService() { return sourceService; }
    public void setSourceService(String sourceService) { this.sourceService = sourceService; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getFunctionName() { return functionName; }
    public void setFunctionName(String functionName) { this.functionName = functionName; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getOperationSource() { return operationSource; }
    public void setOperationSource(String operationSource) { this.operationSource = operationSource; }
    public Boolean getUserVisible() { return userVisible; }
    public void setUserVisible(Boolean userVisible) { this.userVisible = userVisible; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getAlertMessage() { return alertMessage; }
    public void setAlertMessage(String alertMessage) { this.alertMessage = alertMessage; }
    public String getRequestHeadersJson() { return requestHeadersJson; }
    public void setRequestHeadersJson(String requestHeadersJson) { this.requestHeadersJson = requestHeadersJson; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
}
