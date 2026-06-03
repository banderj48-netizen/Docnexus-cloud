package com.xyf.docnexus.user.Controller;

import com.xyf.docnexus.common.DTO.ChangePasswordRequest;
import com.xyf.docnexus.common.DTO.SessionHeartbeatRequest;
import com.xyf.docnexus.common.DTO.UserProfileUpdateRequest;
import com.xyf.docnexus.common.VO.ApiResponse;
import com.xyf.docnexus.common.VO.LoginResponse;
import com.xyf.docnexus.common.VO.PageResponse;
import com.xyf.docnexus.common.VO.UserProfileResponse;
import com.xyf.docnexus.common.VO.UserSessionResponse;
import com.xyf.docnexus.common.log.BusinessOperationLog;
import com.xyf.docnexus.user.Service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class CurrentUserController {
    private final UserService userService;

    public CurrentUserController(UserService userService) {
        this.userService = userService;
    }

    // 获取现在的用户的资料
    @BusinessOperationLog(module = "用户中心", functionName = "查询资料", operationType = "QUERY",
            operationName = "查询当前用户资料", triggerType = "AUTO_QUERY", operationSource = "FRONTEND", userVisible = false)
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getCurrentProfile(
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success("获取用户资料成功", userService.getCurrentProfile(userId));
    }

    // 修改现在的用户的资料
    @BusinessOperationLog(module = "用户中心", functionName = "修改资料", operationType = "UPDATE",
            operationName = "修改个人资料", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @PutMapping("/me/profile")
    public ApiResponse<UserProfileResponse> updateCurrentProfile(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody UserProfileUpdateRequest request) {
        return ApiResponse.success("修改用户资料成功", userService.updateCurrentProfile(userId, request));
    }

    @BusinessOperationLog(module = "用户中心", functionName = "清理资料缓存", operationType = "DELETE",
            operationName = "清理用户资料缓存", triggerType = "INTERNAL_CALL", operationSource = "SERVICE_INTERNAL", userVisible = false)
    @DeleteMapping("/me/profile-cache")
    public ApiResponse<Void> clearCurrentProfileCache(
            @RequestHeader("X-User-Id") Long userId) {
        userService.clearCurrentProfileCache(userId);
        return ApiResponse.success("清理用户资料缓存成功", null);
    }

    @BusinessOperationLog(module = "用户中心", functionName = "修改密码", operationType = "UPDATE",
            operationName = "修改密码", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @PutMapping("/me/password")
    public ApiResponse<LoginResponse> changeCurrentPassword(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestHeader(value = "X-Client-IP", required = false) String clientIp,
            @RequestBody ChangePasswordRequest request) {
        return ApiResponse.success(
                "修改密码成功",
                userService.changeCurrentPassword(userId, authorizationHeader, request, clientIp)
        );
    }

    @BusinessOperationLog(module = "用户中心", functionName = "查询在线会话", operationType = "QUERY",
            operationName = "查询在线会话", triggerType = "AUTO_QUERY", operationSource = "FRONTEND", userVisible = false)
    @GetMapping("/me/sessions")
    public ApiResponse<PageResponse<UserSessionResponse>> listCurrentSessions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "currentSessionId", required = false) String currentSessionId,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "5") Integer pageSize) {
        return ApiResponse.success(
                "获取在线会话成功",
                userService.listCurrentSessions(userId, currentSessionId, pageNum, pageSize)
        );
    }

    @BusinessOperationLog(module = "用户中心", functionName = "会话心跳", operationType = "QUERY",
            operationName = "更新会话在线状态", triggerType = "SYSTEM_POLLING", operationSource = "FRONTEND", userVisible = false)
    @PostMapping("/me/sessions/heartbeat")
    public ApiResponse<Void> heartbeatCurrentSession(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Access-Jti", required = false) String accessJti,
            @RequestBody SessionHeartbeatRequest request) {
        userService.heartbeatCurrentSession(userId, accessJti, request);
        return ApiResponse.success("会话在线状态已更新", null);
    }

    @BusinessOperationLog(module = "用户中心", functionName = "退出设备", operationType = "DELETE",
            operationName = "退出指定设备", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @DeleteMapping("/me/sessions/{sessionId}")
    public ApiResponse<Void> logoutCurrentUserSession(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("sessionId") String sessionId) {
        userService.logoutCurrentUserSession(userId, sessionId);
        return ApiResponse.success("退出会话成功", null);
    }

}
