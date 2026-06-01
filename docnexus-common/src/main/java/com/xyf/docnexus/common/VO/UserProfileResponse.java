package com.xyf.docnexus.common.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户管理页展示资料。
 *
 * 注意：这里绝对不要返回 password。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private Long userId;

    private String username;

    private String email;

    private String phone;

    private String role;

    /**
     * 账号状态原始值，直接来自 user_account.status。
     * 例如：ENABLE、DISABLE。
     */
    private String accountStatus;

    /**
     * 账号状态展示文案，由后端根据数据库状态转换。
     * 前端直接展示该字段，避免前端硬编码“正常”。
     */
    private String accountStatusText;

    /**
     * 最近一次登录时间，毫秒级时间戳。
     * 数据来自 user_session.login_at，由后端查询后随用户资料一起返回并缓存。
     */
    private Long lastLoginAtMillis;

    /**
     * 注册时间，毫秒级时间戳。
     * 数据来自 user_account.create_time，前端按天展示。
     */
    private Long createTimeMillis;
}
