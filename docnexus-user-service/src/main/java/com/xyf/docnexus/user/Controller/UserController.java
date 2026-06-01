package com.xyf.docnexus.user.Controller;

import com.xyf.docnexus.common.DTO.*;
import com.xyf.docnexus.common.VO.ApiResponse;
import com.xyf.docnexus.common.VO.LoginResponse;
import com.xyf.docnexus.common.VO.PasswordRecoveryVerifyResponse;
import com.xyf.docnexus.user.Service.UserService;
import com.xyf.docnexus.user.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证接口。
 */
@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String clientIp = ClientIpUtil.resolveClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);
        return ApiResponse.success("登录成功", userService.login(request, clientIp, userAgent));
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody RegisterRequest request) {
        userService.register(request);
        return ApiResponse.success("注册成功", null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        userService.logout(authorizationHeader);
        return ApiResponse.success("退出登录成功", null);
    }

    @PostMapping("/password/recovery/verify")
    public ApiResponse<PasswordRecoveryVerifyResponse> verifyPasswordRecovery(
            @RequestBody PasswordRecoveryVerifyRequest request) {
        return ApiResponse.success("身份验证通过", userService.verifyPasswordRecovery(request));
    }

    @PostMapping("/password/recovery/reset")
    public ApiResponse<Void> resetPassword(@RequestBody PasswordResetRequest request) {
        userService.resetPassword(request);
        return ApiResponse.success("密码重置成功", null);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestBody RefreshTokenRequest request,
                                              HttpServletRequest servletRequest) {
        String clientIp = ClientIpUtil.resolveClientIp(servletRequest);
        return ApiResponse.success("刷新登录态成功", userService.refreshAccessToken(request, clientIp));
    }

}
