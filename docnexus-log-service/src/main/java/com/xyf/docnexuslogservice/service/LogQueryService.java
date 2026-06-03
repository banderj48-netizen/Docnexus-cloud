package com.xyf.docnexuslogservice.service;

import com.xyf.docnexus.common.VO.PageResponse;
import com.xyf.docnexuslogservice.entity.BusinessOperationLog;
import com.xyf.docnexuslogservice.entity.GatewayAuditLog;
import com.xyf.docnexuslogservice.entity.SecurityAlertLog;

import java.util.Map;

/**
 * 日志查询服务。
 */
public interface LogQueryService {

    /**
     * 分页查询网关审计日志。
     */
    PageResponse<GatewayAuditLog> pageGatewayAudit(Long userId, String path, Integer statusCode, long pageNum, long pageSize);

    /**
     * 查询日志概览统计。
     */
    Map<String, Object> summary();

    /**
     * 分页查询安全告警日志。
     */
    PageResponse<SecurityAlertLog> pageSecurityAlert(String alertType, Integer handled, Long userId, String clientIp, long pageNum, long pageSize);

    /**
     * 标记安全告警为已处理。
     */
    boolean markSecurityAlertHandled(String eventId);

    /**
     * 查询普通用户最近 5 天主动业务操作摘要。
     */
    Map<String, Object> userOperationSummary(Long userId);

    /**
     * 删除当前用户的用户操作 Redis 缓存，用户主动发起新业务操作时由前端静默触发。
     */
    void invalidateUserOperationCache(Long userId);

    /**
     * 分页查询普通用户最近 5 天主动业务操作日志。
     */
    PageResponse<BusinessOperationLog> pageUserOperations(Long userId,
                                                          Boolean success,
                                                          String module,
                                                          String functionName,
                                                          long pageNum,
                                                          long pageSize);
}
