package com.xyf.docnexus.user.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.common.event.BusinessOperationLogEvent;
import com.xyf.docnexus.common.log.BusinessOperationLog;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务业务操作日志 AOP。
 *
 * <p>用户主动调用的日志注解加在 Controller 方法上，避免 Service 被自动查询、MQ 或内部调用复用时误判为用户主动操作。</p>
 */
@Aspect
@Component
public class BusinessOperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(BusinessOperationLogAspect.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final String sourceService;

    public BusinessOperationLogAspect(ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider,
                                      ObjectMapper objectMapper,
                                      @Value("${spring.application.name:docnexus-user-service}") String sourceService) {
        this.rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.sourceService = sourceService;
    }

    /**
     * 包裹 Controller 业务方法，记录成功、失败和业务耗时。
     */
    @Around("@annotation(operationLog)")
    public Object recordOperation(ProceedingJoinPoint joinPoint, BusinessOperationLog operationLog) throws Throwable {
        long startNano = System.nanoTime();
        LocalDateTime occurredAt = LocalDateTime.now();
        try {
            Object result = joinPoint.proceed();
            publishOperation(operationLog, joinPoint.getArgs(), occurredAt, startNano, true, null);
            return result;
        } catch (Throwable throwable) {
            publishOperation(operationLog, joinPoint.getArgs(), occurredAt, startNano, false, throwable.getMessage());
            throw throwable;
        }
    }

    /**
     * 构造业务日志事件并发送到日志 Topic。
     */
    private void publishOperation(BusinessOperationLog operationLog,
                                  Object[] args,
                                  LocalDateTime occurredAt,
                                  long startNano,
                                  boolean success,
                                  String alertMessage) {
        if (rocketMQTemplate == null) {
            log.debug("RocketMQTemplate 不可用，跳过业务操作日志发送，module={}, function={}",
                    operationLog.module(), operationLog.functionName());
            return;
        }
        HttpServletRequest request = currentRequest();
        BusinessOperationLogEvent event = new BusinessOperationLogEvent();
        event.setEventId(UUID.randomUUID().toString().replace("-", ""));
        event.setRequestId(header(request, "X-Request-Id"));
        event.setTraceId(resolveTraceId(request, args));
        event.setUserId(resolveUserId(request, args));
        event.setUsername(resolveString(args, "getUsername", header(request, "X-Username")));
        event.setSourceService(sourceService);
        event.setModule(operationLog.module());
        event.setFunctionName(operationLog.functionName());
        event.setOperationType(operationLog.operationType());
        event.setOperationName(operationLog.operationName());
        event.setTriggerType(operationLog.triggerType());
        event.setOperationSource(operationLog.operationSource());
        event.setUserVisible(operationLog.userVisible());
        event.setBusinessKey(resolveBusinessKey(request, args));
        event.setSuccess(success);
        event.setAlertMessage(success ? null : alertMessage);
        event.setRequestHeadersJson(toHeaderJson(request));
        event.setMethod(request == null ? null : request.getMethod());
        event.setPath(request == null ? null : request.getRequestURI());
        event.setClientIp(header(request, "X-Client-IP"));
        event.setUserAgent(header(request, "User-Agent"));
        event.setOccurredAt(occurredAt);
        event.setCompletedAt(LocalDateTime.now());
        event.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano));
        sendEvent(event);
    }

    /**
     * 异步发送业务日志事件，发送失败只写本地日志，不阻塞用户请求。
     */
    private void sendEvent(BusinessOperationLogEvent event) {
        String destination = MqTopicConstants.LOG_EVENT_TOPIC + ":" + MqTopicConstants.TAG_BUSINESS_OPERATION_LOG;
        rocketMQTemplate.asyncSend(destination, event, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("业务操作日志事件发送成功，eventId={}", event.getEventId());
            }

            @Override
            public void onException(Throwable throwable) {
                log.warn("业务操作日志事件发送失败，eventId={}, traceId={}", event.getEventId(), event.getTraceId(), throwable);
            }
        });
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
    private String resolveTraceId(HttpServletRequest request, Object[] args) {
        String traceId = header(request, "X-Trace-Id");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        String messageTraceId = resolveString(args, "getTraceId", null);
        if (messageTraceId != null && !messageTraceId.isBlank()) {
            return messageTraceId;
        }
        String requestId = header(request, "X-Request-Id");
        return requestId == null || requestId.isBlank() ? resolveString(args, "getEventId", null) : requestId;
    }

    /**
     * 解析 Long 值，失败时返回 null。
     */
    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 从路径变量或请求参数中提取业务键。
     */
    private String resolveBusinessKey(HttpServletRequest request, Object[] args) {
        if (request == null) {
            return resolveMessageBusinessKey(args);
        }
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables instanceof Map<?, ?> map) {
            for (String key : new String[]{"fileId", "uploadId", "sessionId", "taskId"}) {
                Object value = map.get(key);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
        }
        for (String key : new String[]{"fileId", "uploadId", "sessionId", "taskId"}) {
            String value = request.getParameter(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return resolveMessageBusinessKey(args);
    }

    /**
     * 优先从 HTTP 可信头获取用户 ID；MQ 消费等后台入口则从消息对象读取用户 ID。
     */
    private Long resolveUserId(HttpServletRequest request, Object[] args) {
        Long requestUserId = parseLong(header(request, "X-User-Id"));
        return requestUserId == null ? resolveLong(args, "getUserId", null) : requestUserId;
    }

    /**
     * 从 MQ 消息对象中提取业务键，优先使用业务主键，其次使用 sessionId、fileId、eventId。
     */
    private String resolveMessageBusinessKey(Object[] args) {
        for (String getterName : new String[]{"getBusinessKey", "getSessionId", "getFileId", "getUploadId", "getTaskId", "getEventId"}) {
            String value = resolveString(args, getterName, null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 通过 JavaBean getter 从消息对象读取字符串字段，避免每个 Consumer 手写日志代码。
     */
    private String resolveString(Object[] args, String getterName, String fallback) {
        Object value = invokeGetter(args, getterName);
        return value == null ? fallback : String.valueOf(value);
    }

    /**
     * 通过 JavaBean getter 从消息对象读取 Long 字段。
     */
    private Long resolveLong(Object[] args, String getterName, Long fallback) {
        Object value = invokeGetter(args, getterName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? fallback : parseLong(String.valueOf(value));
    }

    /**
     * 安全调用消息对象的无参 getter；没有该字段时返回 null。
     */
    private Object invokeGetter(Object[] args, String getterName) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            try {
                Method method = arg.getClass().getMethod(getterName);
                return method.invoke(arg);
            } catch (ReflectiveOperationException ignored) {
                // 不同消息对象字段不完全一致，找不到 getter 属于正常情况。
            }
        }
        return null;
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
     * 判断请求头是否为敏感头。
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
