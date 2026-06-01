package com.xyf.docnexus.common.DTO;

import lombok.Data;

/**
 * 找回密码后的重置密码请求。
 *
 * password 和 confirmPassword 都是前端 Base64 编码后的密码。
 */
@Data
public class PasswordResetRequest {

    private String username;

    private String resetToken;

    private String password;

    private String confirmPassword;
}
