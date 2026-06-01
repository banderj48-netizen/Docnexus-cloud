package com.xyf.docnexus.common.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 找回密码身份验证响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordRecoveryVerifyResponse {

    /**
     * 是否允许进入重置密码步骤。
     */
    private Boolean allowed;

    /**
     * 一次性重置许可 token。
     */
    private String resetToken;

    /**
     * 重置许可有效期，单位：秒。
     */
    private Long expireSeconds;
}
