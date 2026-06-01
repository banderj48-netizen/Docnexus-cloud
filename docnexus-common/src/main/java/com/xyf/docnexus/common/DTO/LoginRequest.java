package com.xyf.docnexus.common.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    /**
     * 用户名。
     */
    private String username;

    /**
     * 前端 Base64 编码后的密码。
     */
    private String password;

    /**
     * 前端采集的设备指纹原始串。
     *
     * <p>该字段不作为安全认证凭证，只用于后端结合 userId 和登录 IP 计算 deviceId，
     * 从而实现“同一设备只保留一个 ACTIVE 会话”。</p>
     */
    private String deviceFingerprint;

    /**
     * 前端展示用设备名称。
     *
     * <p>例如 Windows、macOS、Android 等。后端会做长度截断和兜底，
     * 避免前端传入异常长字符串影响数据库字段。</p>
     */
    private String deviceName;
}
