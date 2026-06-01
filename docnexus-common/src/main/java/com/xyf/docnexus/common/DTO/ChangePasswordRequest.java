package com.xyf.docnexus.common.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户修改密码请求。
 *
 * 前端会把三个密码字段都进行 Base64 编码后再提交。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    /**
     * 原始密码，Base64 编码后传输。
     */
    private String oldPassword;

    /**
     * 新密码，Base64 编码后传输。
     */
    private String newPassword;

    /**
     * 确认新密码，Base64 编码后传输。
     */
    private String confirmPassword;
}