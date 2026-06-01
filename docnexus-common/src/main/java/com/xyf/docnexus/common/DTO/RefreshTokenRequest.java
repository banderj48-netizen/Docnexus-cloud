package com.xyf.docnexus.common.DTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 刷新 accessToken 请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {

    /**
     * 当前登录会话 ID。
     */
    private String sessionId;

    /**
     * refreshToken 明文。
     * 注意：数据库只保存 hash，不保存明文。
     */
    private String refreshToken;
}