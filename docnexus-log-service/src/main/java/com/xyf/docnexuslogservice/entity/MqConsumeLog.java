package com.xyf.docnexuslogservice.entity;

import java.time.LocalDateTime;

/**
 * MQ 消费幂等日志实体，对应 mq_consume_log。
 */
public class MqConsumeLog {

    private Long id;
    private String eventId;
    private String traceId;
    private String topic;
    private String tag;
    private String consumerGroup;
    private String businessKey;
    private String consumeStatus;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime consumeStartedAt;
    private LocalDateTime consumeFinishedAt;
    private Long durationMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getConsumerGroup() { return consumerGroup; }
    public void setConsumerGroup(String consumerGroup) { this.consumerGroup = consumerGroup; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getConsumeStatus() { return consumeStatus; }
    public void setConsumeStatus(String consumeStatus) { this.consumeStatus = consumeStatus; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getConsumeStartedAt() { return consumeStartedAt; }
    public void setConsumeStartedAt(LocalDateTime consumeStartedAt) { this.consumeStartedAt = consumeStartedAt; }
    public LocalDateTime getConsumeFinishedAt() { return consumeFinishedAt; }
    public void setConsumeFinishedAt(LocalDateTime consumeFinishedAt) { this.consumeFinishedAt = consumeFinishedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
