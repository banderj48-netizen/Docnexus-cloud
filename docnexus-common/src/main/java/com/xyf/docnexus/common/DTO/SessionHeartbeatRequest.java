package com.xyf.docnexus.common.DTO;

import lombok.Data;

/**
 * 用户会话 heartbeat 请求。
 *
 * <p>前端定时携带当前浏览器保存的 sessionId 调用 heartbeat，
 * 后端通过 X-User-Id 再次校验该 session 是否属于当前用户。</p>
 */
@Data
public class SessionHeartbeatRequest {

    /**
     * 当前浏览器登录会话 ID。
     */
    private String sessionId;
}
