package com.xyf.docnexus.user.Controller;

import com.xyf.docnexus.user.config.UserJwtProperties;
import com.xyf.docnexus.user.entity.JwksKeyResponse;
import com.xyf.docnexus.user.entity.JwksResponse;
import com.xyf.docnexus.user.util.JwtSignTool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;

/**
 * 内部 JWKS 公钥接口。
 *
 * <p>该接口只提供 RSA 公钥参数，供 Gateway 多实例按 kid 验签；生产环境应只允许网关内网访问。</p>
 */
@RestController
@RequestMapping("/internal/auth")
public class InternalJwksController {

    private final JwtSignTool jwtSignTool;
    private final UserJwtProperties jwtProperties;

    public InternalJwksController(JwtSignTool jwtSignTool, UserJwtProperties jwtProperties) {
        this.jwtSignTool = jwtSignTool;
        this.jwtProperties = jwtProperties;
    }

    /**
     * 查询当前可用 JWKS 公钥集合。
     *
     * <p>多实例 UserService 必须返回同一个 active kid 对应的公钥，Gateway 会缓存该结果。</p>
     */
    @GetMapping("/jwks")
    public JwksResponse jwks() {
        if (Boolean.FALSE.equals(jwtProperties.getJwks().getEnabled())) {
            throw new IllegalStateException("JWKS 内部接口未启用");
        }
        RSAPublicKey publicKey = jwtSignTool.currentPublicKey();
        JwksKeyResponse key = new JwksKeyResponse();
        key.setKty("RSA");
        key.setUse("sig");
        key.setAlg("RS256");
        key.setKid(jwtSignTool.currentKeyId());
        key.setN(base64UrlUnsigned(publicKey.getModulus()));
        key.setE(base64UrlUnsigned(publicKey.getPublicExponent()));

        JwksResponse response = new JwksResponse();
        response.setKeys(List.of(key));
        return response;
    }

    /**
     * 把 RSA 大整数转换为 JWK 需要的无符号 base64url 字符串。
     */
    private String base64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
