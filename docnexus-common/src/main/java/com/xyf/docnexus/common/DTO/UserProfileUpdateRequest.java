package com.xyf.docnexus.common.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户修改个人资料请求。
 *
 * 只允许用户修改手机号和邮箱，不能让前端传 userId、role、password 等敏感字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateRequest {
    private Long userId;
    /**
     * 用户邮箱。
     */
    private String email;

    /**
     * 用户手机号。
     */
    private String phone;
}