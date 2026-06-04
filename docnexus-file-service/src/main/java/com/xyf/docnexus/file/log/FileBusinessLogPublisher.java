package com.xyf.docnexus.file.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.common.event.BusinessOperationLogEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件服务手动业务日志发布器。
 *
 * <p>Controller 上的用户操作优先由 AOP 记录；OnlyOffice 回调这类代用户保存动作没有 X-User-Id，
 * 需要在 token 验签后由服务层手动补发用户可见日志。</p>
 */
@Slf4j
@Component
public class FileBusinessLogPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final String sourceService;

    public FileBusinessLogPublisher(ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider,
                                    ObjectMapper objectMapper,
                                    @Value("${spring.application.name:docnexus-file-service}") String sourceService) {
        this.rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.sourceService = sourceService;
    }

    /**
     * 在事务提交后记录 OnlyOffice 保存成功日志。
     */
    public void publishOnlyOfficeSaveSuccessAfterCommit(Long userId, String fileId, LocalDateTime occurredAt, long startNano) {
        Runnable publishTask = () -> publishOnlyOfficeSave(userId, fileId, occurredAt, startNano, true, null);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishTask.run();
                }
            });
            return;
        }
        publishTask.run();
    }

    /**
     * 记录 OnlyOffice 保存失败日志。
     */
    public void publishOnlyOfficeSaveFailure(Long userId, String fileId, LocalDateTime occurredAt, long startNano, String alertMessage) {
        publishOnlyOfficeSave(userId, fileId, occurredAt, startNano, false, alertMessage);
    }

    /**
     * 构造并发送 OnlyOffice 保存业务日志。
     */
    private void publishOnlyOfficeSave(Long userId,
                                       String fileId,
                                       LocalDateTime occurredAt,
                                       long startNano,
                                       boolean success,
                                       String alertMessage) {
        if (rocketMQTemplate == null) {
            log.debug("RocketMQTemplate 不可用，跳过 OnlyOffice 保存业务日志，fileId={}", fileId);
            return;
        }
        HttpServletRequest request = currentRequest();
        BusinessOperationLogEvent event = new BusinessOperationLogEvent();
        event.setEventId(UUID.randomUUID().toString().replace("-", ""));
        event.setRequestId(header(request, "X-Request-Id"));
        event.setTraceId(resolveTraceId(request));
        event.setUserId(userId);
        event.setUsername(null);
        event.setSourceService(sourceService);
        event.setModule("文件服务");
        event.setFunctionName("保存 OnlyOffice 文档");
        event.setOperationType("UPDATE");
        event.setOperationName("保存原样编辑文档");
        event.setTriggerType("USER_ACTION");
        event.setOperationSource("FRONTEND");
        event.setUserVisible(true);
        event.setBusinessKey(fileId);
        event.setSuccess(success);
        event.setAlertMessage(success ? null : alertMessage);
        event.setRequestHeadersJson(toHeaderJson(request));
        event.setMethod(request == null ? "POST" : request.getMethod());
        event.setPath(request == null ? null : request.getRequestURI());
        event.setClientIp(header(request, "X-Client-IP"));
        event.setUserAgent(header(request, "User-Agent"));
        event.setOccurredAt(occurredAt);
        event.setCompletedAt(LocalDateTime.now());
        event.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano));
        sendEvent(event);
    }

    /**
     * 异步投递业务日志事件。
     */
    private void sendEvent(BusinessOperationLogEvent event) {
        String destination = MqTopicConstants.LOG_EVENT_TOPIC + ":" + MqTopicConstants.TAG_BUSINESS_OPERATION_LOG;
        try {
            rocketMQTemplate.asyncSend(destination, event, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("OnlyOffice 保存业务日志发送成功，eventId={}", event.getEventId());
                }

                @Override
                public void onException(Throwable throwable) {
                    log.warn("OnlyOffice 保存业务日志发送失败，eventId={}, fileId={}",
                            event.getEventId(), event.getBusinessKey(), throwable);
                }
            });
        } catch (Exception exception) {
            log.warn("OnlyOffice 保存业务日志发送异常，eventId={}, fileId={}",
                    event.getEventId(), event.getBusinessKey(), exception);
        }
    }

    /**
     * 获取当前 HTTP 请求。
     */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /**
     * 获取请求头。
     */
    private String header(HttpServletRequest request, String name) {
        return request == null ? null : request.getHeader(name);
    }

    /**
     * 解析 traceId，缺失时退回 requestId。
     */
    private String resolveTraceId(HttpServletRequest request) {
        String traceId = header(request, "X-Trace-Id");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        String requestId = header(request, "X-Request-Id");
        return requestId == null || requestId.isBlank() ? null : requestId;
    }

    /**
     * 序列化脱敏请求头。
     */
    private String toHeaderJson(HttpServletRequest request) {
        if (request == null) {
            return "{}";
        }
        Map<String, String> safeHeaders = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (!isSensitiveHeader(name)) {
                safeHeaders.put(name, request.getHeader(name));
            }
        }
        try {
            return objectMapper.writeValueAsString(safeHeaders);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    /**
     * 判断请求头是否敏感。
     */
    private boolean isSensitiveHeader(String name) {
        String lowerName = name == null ? "" : name.toLowerCase();
        return lowerName.contains("authorization")
                || lowerName.contains("cookie")
                || lowerName.contains("token")
                || lowerName.contains("password")
                || lowerName.contains("secret");
    }
}
