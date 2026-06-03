package com.xyf.docnexuslogservice.service.impl;

import com.xyf.docnexus.common.event.BusinessOperationLogEvent;
import com.xyf.docnexus.common.event.GatewayAuditEvent;
import com.xyf.docnexus.common.event.SecurityAlertEvent;
import com.xyf.docnexuslogservice.entity.BusinessOperationLog;
import com.xyf.docnexuslogservice.entity.GatewayAuditLog;
import com.xyf.docnexuslogservice.entity.MqConsumeLog;
import com.xyf.docnexuslogservice.entity.SecurityAlertLog;
import com.xyf.docnexuslogservice.mapper.BusinessOperationLogMapper;
import com.xyf.docnexuslogservice.mapper.GatewayAuditLogMapper;
import com.xyf.docnexuslogservice.mapper.MqConsumeLogMapper;
import com.xyf.docnexuslogservice.mapper.SecurityAlertLogMapper;
import com.xyf.docnexuslogservice.service.GatewayLogIngestService;
import com.xyf.docnexuslogservice.service.LogQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 日志事件入库服务实现。
 */
@Service
public class GatewayLogIngestServiceImpl implements GatewayLogIngestService {

    private final GatewayAuditLogMapper gatewayAuditLogMapper;
    private final SecurityAlertLogMapper securityAlertLogMapper;
    private final MqConsumeLogMapper mqConsumeLogMapper;
    private final BusinessOperationLogMapper businessOperationLogMapper;
    private final LogQueryService logQueryService;

    public GatewayLogIngestServiceImpl(GatewayAuditLogMapper gatewayAuditLogMapper,
                                       SecurityAlertLogMapper securityAlertLogMapper,
                                       MqConsumeLogMapper mqConsumeLogMapper,
                                       BusinessOperationLogMapper businessOperationLogMapper,
                                       LogQueryService logQueryService) {
        this.gatewayAuditLogMapper = gatewayAuditLogMapper;
        this.securityAlertLogMapper = securityAlertLogMapper;
        this.mqConsumeLogMapper = mqConsumeLogMapper;
        this.businessOperationLogMapper = businessOperationLogMapper;
        this.logQueryService = logQueryService;
    }

    /**
     * 消费并落库网关请求审计事件。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ingestAudit(GatewayAuditEvent event, String topic, String tag, String consumerGroup) {
        consumeWithLog(event.getEventId(), event.getTraceId(), event.getRequestId(), topic, tag, consumerGroup,
                () -> gatewayAuditLogMapper.insertIgnore(toGatewayAuditLog(event)));
    }

    /**
     * 消费并落库安全告警事件。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ingestSecurityAlert(SecurityAlertEvent event, String topic, String tag, String consumerGroup) {
        consumeWithLog(event.getEventId(), event.getTraceId(), event.getRequestId(), topic, tag, consumerGroup,
                () -> securityAlertLogMapper.insertIgnore(toSecurityAlertLog(event)));
    }

    /**
     * 消费并落库业务操作耗时事件。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ingestBusinessOperation(BusinessOperationLogEvent event, String topic, String tag, String consumerGroup) {
        consumeWithLog(event.getEventId(), event.getTraceId(), event.getBusinessKey(), topic, tag, consumerGroup,
                () -> {
                    businessOperationLogMapper.insertIgnore(toBusinessOperationLog(event));
                    invalidateUserOperationCacheAfterCommit(event.getUserId());
                });
    }

    /**
     * 在业务日志事务提交后删除用户操作统计缓存，避免提交前删除后又被旧数据重新写入 Redis。
     */
    private void invalidateUserOperationCacheAfterCommit(Long userId) {
        if (userId == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            logQueryService.invalidateUserOperationCache(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                logQueryService.invalidateUserOperationCache(userId);
            }
        });
    }

    /**
     * 统一包装 MQ 消费幂等、耗时、成功和失败状态记录。
     */
    private void consumeWithLog(String eventId,
                                String traceId,
                                String businessKey,
                                String topic,
                                String tag,
                                String consumerGroup,
                                Runnable action) {
        long startNano = System.nanoTime();
        LocalDateTime startedAt = LocalDateTime.now();
        if (!claimEvent(eventId, traceId, topic, tag, consumerGroup, businessKey, startedAt)) {
            return;
        }
        try {
            action.run();
            mqConsumeLogMapper.markSuccess(eventId, consumerGroup, LocalDateTime.now(), elapsedMs(startNano));
        } catch (Exception exception) {
            mqConsumeLogMapper.markFailed(eventId, consumerGroup, LocalDateTime.now(), elapsedMs(startNano), exception.getMessage());
            throw exception;
        }
    }

    /**
     * 抢占 eventId + consumerGroup 的消费权。
     */
    private boolean claimEvent(String eventId,
                               String traceId,
                               String topic,
                               String tag,
                               String consumerGroup,
                               String businessKey,
                               LocalDateTime startedAt) {
        MqConsumeLog log = new MqConsumeLog();
        log.setEventId(eventId);
        log.setTraceId(traceId);
        log.setTopic(topic);
        log.setTag(tag);
        log.setConsumerGroup(consumerGroup);
        log.setBusinessKey(businessKey);
        log.setConsumeStartedAt(startedAt);
        return mqConsumeLogMapper.claimProcessing(log) > 0;
    }

    /**
     * 计算消费耗时，单位毫秒。
     */
    private long elapsedMs(long startNano) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
    }

    /**
     * 将网关审计事件转换为实体。
     */
    private GatewayAuditLog toGatewayAuditLog(GatewayAuditEvent event) {
        GatewayAuditLog log = new GatewayAuditLog();
        log.setEventId(event.getEventId());
        log.setRequestId(event.getRequestId());
        log.setTraceId(event.getTraceId());
        log.setUserId(event.getUserId());
        log.setUsername(event.getUsername());
        log.setClientIp(event.getClientIp());
        log.setMethod(event.getMethod());
        log.setPath(event.getPath());
        log.setRouteId(event.getRouteId());
        log.setTargetService(event.getTargetService());
        log.setRequestKind(event.getRequestKind());
        log.setStatusCode(event.getStatusCode());
        log.setUserAgent(event.getUserAgent());
        log.setErrorMessage(event.getErrorMessage());
        log.setOccurredAt(event.getOccurredAt());
        return log;
    }

    /**
     * 将安全告警事件转换为实体。
     */
    private SecurityAlertLog toSecurityAlertLog(SecurityAlertEvent event) {
        SecurityAlertLog log = new SecurityAlertLog();
        log.setEventId(event.getEventId());
        log.setRequestId(event.getRequestId());
        log.setTraceId(event.getTraceId());
        log.setAlertType(event.getAlertType());
        log.setAlertLevel(event.getAlertLevel());
        log.setUserId(event.getUserId());
        log.setClientIp(event.getClientIp());
        log.setMethod(event.getMethod());
        log.setPath(event.getPath());
        log.setMessage(event.getMessage());
        log.setDetailJson(event.getDetailJson());
        log.setOccurredAt(event.getOccurredAt());
        return log;
    }

    /**
     * 将业务操作日志事件转换为实体。
     */
    private BusinessOperationLog toBusinessOperationLog(BusinessOperationLogEvent event) {
        BusinessOperationLog log = new BusinessOperationLog();
        log.setEventId(event.getEventId());
        log.setRequestId(event.getRequestId());
        log.setTraceId(event.getTraceId());
        log.setUserId(event.getUserId());
        log.setUsername(event.getUsername());
        log.setSourceService(event.getSourceService());
        log.setModule(event.getModule());
        log.setFunctionName(event.getFunctionName());
        log.setOperationType(event.getOperationType());
        log.setOperationName(event.getOperationName());
        log.setTriggerType(event.getTriggerType());
        log.setOperationSource(event.getOperationSource());
        log.setUserVisible(Boolean.TRUE.equals(event.getUserVisible()));
        log.setBusinessKey(event.getBusinessKey());
        log.setSuccess(Boolean.TRUE.equals(event.getSuccess()));
        log.setAlertMessage(event.getAlertMessage());
        log.setRequestHeadersJson(event.getRequestHeadersJson());
        log.setMethod(event.getMethod());
        log.setPath(event.getPath());
        log.setClientIp(event.getClientIp());
        log.setUserAgent(event.getUserAgent());
        log.setOccurredAt(event.getOccurredAt());
        log.setCompletedAt(event.getCompletedAt());
        log.setDurationMs(event.getDurationMs());
        return log;
    }
}
