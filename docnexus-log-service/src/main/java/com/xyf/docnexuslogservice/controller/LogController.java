package com.xyf.docnexuslogservice.controller;

import com.xyf.docnexus.common.VO.ApiResponse;
import com.xyf.docnexus.common.VO.PageResponse;
import com.xyf.docnexuslogservice.entity.BusinessOperationLog;
import com.xyf.docnexuslogservice.entity.GatewayAuditLog;
import com.xyf.docnexuslogservice.entity.SecurityAlertLog;
import com.xyf.docnexuslogservice.service.LogQueryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 日志查询与告警处理接口。
 */
@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogQueryService logQueryService;

    public LogController(LogQueryService logQueryService) {
        this.logQueryService = logQueryService;
    }

    /**
     * 分页查询网关请求审计日志。
     */
    @GetMapping("/gateway-audits")
    public ApiResponse<PageResponse<GatewayAuditLog>> pageGatewayAudit(@RequestParam(value = "userId", required = false) Long userId,
                                                                       @RequestParam(value = "path", required = false) String path,
                                                                       @RequestParam(value = "statusCode", required = false) Integer statusCode,
                                                                       @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                                                       @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        return ApiResponse.success(logQueryService.pageGatewayAudit(userId, path, statusCode, pageNum, pageSize));
    }

    /**
     * 查询日志概览统计。
     */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.success(logQueryService.summary());
    }

    /**
     * 查询当前用户最近 5 天主动业务操作摘要。
     */
    @GetMapping("/user-operations/summary")
    public ApiResponse<Map<String, Object>> userOperationSummary(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(logQueryService.userOperationSummary(userId));
    }

    /**
     * 删除当前用户的用户操作 Redis 缓存，保证新的主动业务操作发生后统计能重新从 MySQL 聚合。
     */
    @DeleteMapping("/user-operations/cache")
    public ApiResponse<Void> invalidateUserOperationCache(@RequestHeader("X-User-Id") Long userId) {
        logQueryService.invalidateUserOperationCache(userId);
        return ApiResponse.success();
    }

    /**
     * 分页查询当前用户最近 5 天主动业务操作日志。
     */
    @GetMapping("/user-operations")
    public ApiResponse<PageResponse<BusinessOperationLog>> pageUserOperations(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "success", required = false) Boolean success,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "functionName", required = false) String functionName,
            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        return ApiResponse.success(logQueryService.pageUserOperations(userId, success, module, functionName, pageNum, pageSize));
    }

    /**
     * 分页查询安全告警日志。
     */
    @GetMapping("/security-alerts")
    public ApiResponse<PageResponse<SecurityAlertLog>> pageSecurityAlert(@RequestParam(value = "alertType", required = false) String alertType,
                                                                        @RequestParam(value = "handled", required = false) Integer handled,
                                                                        @RequestParam(value = "userId", required = false) Long userId,
                                                                        @RequestParam(value = "clientIp", required = false) String clientIp,
                                                                        @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                                                        @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        return ApiResponse.success(logQueryService.pageSecurityAlert(alertType, handled, userId, clientIp, pageNum, pageSize));
    }

    /**
     * 标记安全告警为已处理。
     */
    @PatchMapping("/security-alerts/{eventId}/handled")
    public ApiResponse<Map<String, Object>> markSecurityAlertHandled(@PathVariable("eventId") String eventId) {
        boolean handled = logQueryService.markSecurityAlertHandled(eventId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", eventId);
        result.put("handled", handled);
        return ApiResponse.success(result);
    }
}
