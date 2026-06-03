package com.xyf.docnexus.user.entity;

import lombok.Data;

import java.util.List;

/**
 * JWKS 公钥集合响应。
 *
 * <p>当前阶段返回 active 公钥；后续密钥轮换时可同时返回旧公钥和新公钥。</p>
 */
@Data
public class JwksResponse {
    private List<JwksKeyResponse> keys;
}
