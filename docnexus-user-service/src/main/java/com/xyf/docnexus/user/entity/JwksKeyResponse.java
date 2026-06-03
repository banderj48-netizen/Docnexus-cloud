package com.xyf.docnexus.user.entity;

import lombok.Data;

/**
 * JWKS RSA 公钥条目。
 *
 * <p>字段名遵循 JSON Web Key 规范，Gateway 根据 kid 和 RSA 公钥参数完成验签。</p>
 */
@Data
public class JwksKeyResponse {
    private String kty;
    private String use;
    private String alg;
    private String kid;
    private String n;
    private String e;
}
