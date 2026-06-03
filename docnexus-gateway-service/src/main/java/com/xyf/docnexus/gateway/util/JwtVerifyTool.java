package com.xyf.docnexus.gateway.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.xyf.docnexus.gateway.config.GatewayJwtProperties;
import com.xyf.docnexus.gateway.security.JwksCacheService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPublicKey;

/**
 * gateway-service 使用的 JWT 验签工具。
 *
 * <p>该工具只持有公钥证书，用来校验 user-service 私钥签发的 JWT。
 * 公钥只能验证签名，不能伪造新令牌，所以适合分发到网关和其他只读校验方。</p>
 */
@Component
public class JwtVerifyTool {

    private static final String BEARER_PREFIX = "Bearer ";

    private final GatewayJwtProperties jwtProperties;
    private final ResourceLoader resourceLoader;
    private final JwksCacheService jwksCacheService;
    private final JWTVerifier jwtVerifier;

    public JwtVerifyTool(GatewayJwtProperties jwtProperties,
                         ResourceLoader resourceLoader,
                         JwksCacheService jwksCacheService) {
        this.jwtProperties = jwtProperties;
        this.resourceLoader = resourceLoader;
        this.jwksCacheService = jwksCacheService;
        this.jwtVerifier = JWT.require(Algorithm.RSA256(loadPublicKey(), null))
                .withIssuer(jwtProperties.getIssuer())
                .acceptLeeway(jwtProperties.getClockSkewSeconds())
                .build();
    }

    /**
     * 校验 JWT 并返回解析后的用户 ID。
     *
     * <p>当前登录方案要求 JWT 中只保存用户 ID。
     * user-service 签发令牌时把用户 ID 写入 subject，
     * gateway-service 验证令牌后也只从 subject 中读取用户 ID。</p>
     */
    public UserTokenPayload verify(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        return verifyToken(token);
    }

    /**
     * 校验 JWT 原始 token 并返回解析后的用户身份。
     *
     * <p>Gateway Filter 会先提取 Bearer token 计算本地缓存 key。
     * 当本地缓存未命中时，再调用该方法执行真正的 RSA 验签。</p>
     */
    public UserTokenPayload verifyToken(String token) {
        DecodedJWT decodedJWT = verifyWithJwksOrFallback(token);

        Long expiresAtMillis = decodedJWT.getClaim("expiresAtMillis").asLong();
        if (expiresAtMillis == null || System.currentTimeMillis() >= expiresAtMillis) {
            throw new JWTVerificationException("JWT 已过期");
        }

        Long tokenVersion = decodedJWT.getClaim("tokenVersion").asLong();
        if (tokenVersion == null || tokenVersion <= 0) {
            throw new JWTVerificationException("JWT 缺少合法 tokenVersion");
        }

        return new UserTokenPayload(
                Long.valueOf(decodedJWT.getSubject()),
                decodedJWT.getClaim("username").asString(),
                decodedJWT.getClaim("role").asString(),
                decodedJWT.getId(),
                decodedJWT.getClaim("issuedAtMillis").asLong(),
                expiresAtMillis,
                tokenVersion
        );
    }

    /**
     * 优先使用 JWKS kid 验签，失败时回退到本地公钥证书。
     */
    private DecodedJWT verifyWithJwksOrFallback(String token) {
        DecodedJWT unverified = JWT.decode(token);
        String keyId = unverified.getKeyId();
        return jwksCacheService.findPublicKey(keyId)
                .map(publicKey -> JWT.require(Algorithm.RSA256(publicKey, null))
                        .withIssuer(jwtProperties.getIssuer())
                        .acceptLeeway(jwtProperties.getClockSkewSeconds())
                        .build()
                        .verify(token))
                .orElseGet(() -> jwtVerifier.verify(token));
    }

    /**
     * 去掉前端请求头中的 Bearer 前缀，只保留 JWT 本体。
     */
    public String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new IllegalArgumentException("请求头中缺少 Authorization");
        }

        if (authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        }

        return authorizationHeader.trim();
    }

    /**
     * 从 X.509 证书文件中读取 RSA 公钥。
     */
    private RSAPublicKey loadPublicKey() {
        try {
            Resource resource = resourceLoader.getResource(jwtProperties.getPublicCert().getLocation());
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

            try (InputStream inputStream = resource.getInputStream()) {
                Certificate certificate = certificateFactory.generateCertificate(inputStream);
                PublicKey publicKey = certificate.getPublicKey();

                if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
                    throw new IllegalStateException("JWT 公钥证书中的公钥不是 RSA 公钥");
                }

                return rsaPublicKey;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("加载 JWT 公钥证书失败，请检查证书位置和证书格式", exception);
        }
    }

    /**
     * 网关从 JWT 中提取出的用户身份信息。
     *
     * <p>当前只保存用户 ID，后续服务如果需要用户名、角色等信息，
     * 可以根据 X-User-Id 再查询 user-service 或本地缓存。</p>
     */
    public record UserTokenPayload(
            Long userId,
            String username,
            String role,
            String jwtId,
            Long issuedAtMillis,
            Long expiresAtMillis,
            Long tokenVersion
    ) {
    }
}
