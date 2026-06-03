package com.xyf.docnexuslogservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyf.docnexus.common.VO.PageResponse;
import com.xyf.docnexuslogservice.entity.BusinessOperationLog;
import com.xyf.docnexuslogservice.entity.GatewayAuditLog;
import com.xyf.docnexuslogservice.entity.SecurityAlertLog;
import com.xyf.docnexuslogservice.mapper.BusinessOperationLogMapper;
import com.xyf.docnexuslogservice.mapper.GatewayAuditLogMapper;
import com.xyf.docnexuslogservice.mapper.SecurityAlertLogMapper;
import com.xyf.docnexuslogservice.service.LogQueryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志查询服务实现。
 */
@Service
public class LogQueryServiceImpl implements LogQueryService {

    private static final int USER_VISIBLE_DAYS = 5;
    private static final Duration USER_OPERATION_SUMMARY_CACHE_TTL = Duration.ofSeconds(60);
    private static final String USER_OPERATION_SUMMARY_CACHE_PREFIX = "docnexus:log:user-operations:summary:";

    private final GatewayAuditLogMapper gatewayAuditLogMapper;
    private final SecurityAlertLogMapper securityAlertLogMapper;
    private final BusinessOperationLogMapper businessOperationLogMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public LogQueryServiceImpl(GatewayAuditLogMapper gatewayAuditLogMapper,
                               SecurityAlertLogMapper securityAlertLogMapper,
                               BusinessOperationLogMapper businessOperationLogMapper,
                               ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                               ObjectMapper objectMapper) {
        this.gatewayAuditLogMapper = gatewayAuditLogMapper;
        this.securityAlertLogMapper = securityAlertLogMapper;
        this.businessOperationLogMapper = businessOperationLogMapper;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询网关审计日志。
     */
    @Override
    public PageResponse<GatewayAuditLog> pageGatewayAudit(Long userId, String path, Integer statusCode, long pageNum, long pageSize) {
        long safePageNum = Math.max(1L, pageNum);
        long safePageSize = Math.min(Math.max(1L, pageSize), 100L);
        long offset = (safePageNum - 1L) * safePageSize;
        long total = gatewayAuditLogMapper.count(userId, path, statusCode);
        List<GatewayAuditLog> records = gatewayAuditLogMapper.selectPage(userId, path, statusCode, Math.toIntExact(offset), Math.toIntExact(safePageSize));
        return PageResponse.of(records, total, Math.toIntExact(safePageNum), Math.toIntExact(safePageSize));
    }

    /**
     * 分页查询安全告警日志。
     */
    @Override
    public PageResponse<SecurityAlertLog> pageSecurityAlert(String alertType, Integer handled, Long userId, String clientIp, long pageNum, long pageSize) {
        long safePageNum = Math.max(1L, pageNum);
        long safePageSize = Math.min(Math.max(1L, pageSize), 100L);
        long offset = (safePageNum - 1L) * safePageSize;
        long total = securityAlertLogMapper.count(alertType, handled);
        List<SecurityAlertLog> records = securityAlertLogMapper.selectPage(alertType, handled, Math.toIntExact(offset), Math.toIntExact(safePageSize));
        return PageResponse.of(records, total, Math.toIntExact(safePageNum), Math.toIntExact(safePageSize));
    }

    /**
     * 标记安全告警为已处理。
     */
    @Override
    public boolean markSecurityAlertHandled(String eventId) {
        return securityAlertLogMapper.markHandled(eventId) > 0;
    }

    /**
     * 查询管理员日志摘要。
     */
    @Override
    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("auditStatus", gatewayAuditLogMapper.countByStatusGroup());
        result.put("alertType", securityAlertLogMapper.countByAlertType());
        return result;
    }

    /**
     * 查询普通用户最近 5 天主动业务操作摘要。
     */
    @Override
    public Map<String, Object> userOperationSummary(Long userId) {
        String cacheKey = userOperationSummaryCacheKey(userId);
        Map<String, Object> cached = readUserOperationSummaryCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        LocalDateTime since = LocalDateTime.now().minusDays(USER_VISIBLE_DAYS);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successStatus", businessOperationLogMapper.countUserVisibleBySuccess(userId, since));
        result.put("functionStats", businessOperationLogMapper.countUserVisibleByFunction(userId, since));
        result.put("days", USER_VISIBLE_DAYS);
        writeUserOperationSummaryCache(cacheKey, result);
        return result;
    }

    /**
     * 删除当前用户的用户操作统计缓存，新业务操作发生后由前端静默调用。
     */
    @Override
    public void invalidateUserOperationCache(Long userId) {
        if (redisTemplate == null || userId == null) {
            return;
        }
        try {
            redisTemplate.delete(userOperationSummaryCacheKey(userId));
        } catch (RuntimeException ignored) {
            // Redis 失效不影响用户业务操作，后续查询可直接回源 MySQL。
        }
    }

    /**
     * 分页查询普通用户最近 5 天主动业务操作。
     */
    @Override
    public PageResponse<BusinessOperationLog> pageUserOperations(Long userId,
                                                                 Boolean success,
                                                                 String module,
                                                                 String functionName,
                                                                 long pageNum,
                                                                 long pageSize) {
        long safePageNum = Math.max(1L, pageNum);
        long safePageSize = sanitizeUserPageSize(pageSize);
        long offset = (safePageNum - 1L) * safePageSize;
        LocalDateTime since = LocalDateTime.now().minusDays(USER_VISIBLE_DAYS);
        long total = businessOperationLogMapper.countUserVisible(userId, success, module, functionName, since);
        List<BusinessOperationLog> records = businessOperationLogMapper.selectUserVisiblePage(
                userId, success, module, functionName, since, Math.toIntExact(offset), Math.toIntExact(safePageSize)
        );
        return PageResponse.of(records, total, Math.toIntExact(safePageNum), Math.toIntExact(safePageSize));
    }

    /**
     * 普通用户日志页只允许 10、20、50 三种分页大小。
     */
    private long sanitizeUserPageSize(long pageSize) {
        if (pageSize == 20L || pageSize == 50L) {
            return pageSize;
        }
        return 10L;
    }

    /**
     * 生成当前用户操作统计缓存 key。
     */
    private String userOperationSummaryCacheKey(Long userId) {
        return USER_OPERATION_SUMMARY_CACHE_PREFIX + userId;
    }

    /**
     * 从 Redis 读取用户操作统计缓存，读取失败时直接回源 MySQL。
     */
    private Map<String, Object> readUserOperationSummaryCache(String cacheKey) {
        if (redisTemplate == null) {
            return null;
        }
        String value;
        try {
            value = redisTemplate.opsForValue().get(cacheKey);
        } catch (RuntimeException ignored) {
            return null;
        }
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException ignored) {
            try {
                redisTemplate.delete(cacheKey);
            } catch (RuntimeException ignoredDeleteFailure) {
                // 删除坏缓存失败时直接回源 MySQL，不阻断查询。
            }
            return null;
        }
    }

    /**
     * 写入 Redis 用户操作统计缓存，缓存失败不影响主查询。
     */
    private void writeUserOperationSummaryCache(String cacheKey, Map<String, Object> result) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), USER_OPERATION_SUMMARY_CACHE_TTL);
        } catch (JsonProcessingException | RuntimeException ignored) {
            // 缓存只是优化手段，写入失败时保留 MySQL 查询结果返回。
        }
    }
}
