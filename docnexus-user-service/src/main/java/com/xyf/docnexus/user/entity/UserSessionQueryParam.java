package com.xyf.docnexus.user.entity;

import lombok.Data;

/**
 * 用户会话查询参数，避免 Mapper 多参数。
 */
@Data
public class UserSessionQueryParam {
    private String sessionId;
    private String accessJti;
    private String deviceId;
    private String clientIp;
    private Long userId;

    /**
     * 当前页码，从 1 开始。
     */
    private Integer pageNum;

    /**
     * 每页条数，只允许业务层传入 5、10、20。
     */
    private Integer pageSize;

    /**
     * MySQL limit 起始偏移量。
     */
    private Integer offset;
}
