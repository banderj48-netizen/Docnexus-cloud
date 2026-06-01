package com.xyf.docnexus.common.DTO;

import lombok.Data;

/**
 * 找回密码身份验证请求。
 *
 * email 和 phone 都是前端 Base64 编码后的字符串。
 */
@Data
public class PasswordRecoveryVerifyRequest {

    private String username;

    private String email;

    private String phone;
}
