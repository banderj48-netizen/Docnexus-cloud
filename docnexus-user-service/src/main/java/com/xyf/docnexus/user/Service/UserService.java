package com.xyf.docnexus.user.Service;

import com.xyf.docnexus.common.DTO.*;
import com.xyf.docnexus.common.VO.LoginResponse;
import com.xyf.docnexus.common.VO.PageResponse;
import com.xyf.docnexus.common.VO.PasswordRecoveryVerifyResponse;
import com.xyf.docnexus.common.VO.UserProfileResponse;
import com.xyf.docnexus.common.VO.UserSessionResponse;

public interface UserService {
    LoginResponse login(LoginRequest request, String clientIp, String userAgent);

    void register(RegisterRequest request);

    void logout(String authorizationHeader);

    PasswordRecoveryVerifyResponse verifyPasswordRecovery(PasswordRecoveryVerifyRequest request);

    void resetPassword(PasswordResetRequest request);

    UserProfileResponse getCurrentProfile(Long userId);

    UserProfileResponse updateCurrentProfile(Long userId, UserProfileUpdateRequest request);

    void clearCurrentProfileCache(Long userId);

    LoginResponse changeCurrentPassword(Long userId,
                                        String authorizationHeader,
                                        ChangePasswordRequest request,
                                        String clientIp);

    LoginResponse refreshAccessToken(RefreshTokenRequest request, String clientIp);

    PageResponse<UserSessionResponse> listCurrentSessions(Long userId, String currentSessionId, Integer pageNum, Integer pageSize);

    void heartbeatCurrentSession(Long userId, String accessJti, SessionHeartbeatRequest request);

    void logoutCurrentUserSession(Long userId, String sessionId);
}
