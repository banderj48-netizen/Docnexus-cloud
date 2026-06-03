package com.xyf.docnexus.user.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.xyf.docnexus.user.config.UserJwtProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * user-service 使用的 JWT 签发工具。
 *
 * <p>当前采用 RSA 非对称签名：
 * user-service 读取私钥并签发令牌，gateway-service 读取公钥证书并校验令牌。
 * 这样即使 gateway-service 被部署到多台机器，也只需要分发公钥，不需要泄露私钥。</p>
 */
@Component
public class JwtSignTool {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserJwtProperties jwtProperties;
    private final ResourceLoader resourceLoader;
    private final Algorithm algorithm;
    private final JWTVerifier jwtVerifier;

    public JwtSignTool(UserJwtProperties jwtProperties, ResourceLoader resourceLoader) {
        this.jwtProperties = jwtProperties;
        this.resourceLoader = resourceLoader;
        this.algorithm = Algorithm.RSA256(null, loadPrivateKey());
        this.jwtVerifier = JWT.require(Algorithm.RSA256(loadPublicKey(), null))
                .withIssuer(jwtProperties.getIssuer())
                .build();
    }

    /**
     * 签发用户登录令牌。
     *
     * @param userId 用户主键，写入 JWT 的 subject 和 userId 声明
     * @param username 用户名，写入 username 声明，便于后续排查和轻量展示
     * @param role 用户角色，写入 role 声明，后续可用于权限判断
     * @param tokenVersion 令牌版本，写入 tokenVersion 声明，用于后续检查令牌版本
     * @return SignedJwt，返回给前端后通常放入 Authorization: Bearer xxx
     */

    public SignedJwt signWithSessionInfo(Long userId, String username, String role, Long tokenVersion) {
        long issuedAtMillis = System.currentTimeMillis();
        long expiresAtMillis = issuedAtMillis + jwtProperties.getExpireSeconds() * 1000L;
        String jwtId = UUID.randomUUID().toString();

        String token = JWT.create()
                .withHeader(Map.of("kid", jwtProperties.getKeyId()))
                .withIssuer(jwtProperties.getIssuer())
                .withSubject(String.valueOf(userId))
                .withJWTId(jwtId)
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withClaim("role", role)
                .withClaim("tokenVersion", tokenVersion)
                .withClaim("issuedAtMillis", issuedAtMillis)
                .withClaim("expiresAtMillis", expiresAtMillis)
                .withIssuedAt(new Date(issuedAtMillis))
                .withExpiresAt(new Date(expiresAtMillis))
                .sign(algorithm);

        return new SignedJwt(token, jwtId, issuedAtMillis, expiresAtMillis, tokenVersion);
    }

    /**
     * 返回当前 active JWT 密钥 ID。
     *
     * <p>JWKS 接口使用该值标识当前公钥，Gateway 会根据 Token Header 中的 kid 找到同一把公钥。</p>
     */
    public String currentKeyId() {
        return jwtProperties.getKeyId();
    }

    /**
     * 返回当前 active 公钥。
     *
     * <p>该方法只暴露 RSA 公钥，不能用于签发 Token，可安全用于 JWKS 输出。</p>
     */
    public RSAPublicKey currentPublicKey() {
        return loadPublicKey();
    }



    /**
     * 校验并解析当前登录令牌。
     *
     * <p>退出登录接口虽然通常会先经过 gateway-service 校验，但 user-service
     * 这里仍然做一次签名和过期时间校验，避免有人绕过网关直接调用用户服务。</p>
     */
    public DecodedJWT verify(String authorizationHeader) {
        String token = removeBearerPrefix(authorizationHeader);
        return jwtVerifier.verify(token);
    }

    /**
     * 从 PKCS12 密钥库中读取 RSA 私钥。
     *
     * <p>这个方法只在工具类初始化时执行一次，避免每次登录签发令牌都重复打开密钥库。</p>
     */
    private RSAPrivateKey loadPrivateKey() {
        UserJwtProperties.PrivateKeyStore keyStoreProperties = jwtProperties.getPrivateKeyStore();

        try {
            KeyStore keyStore = KeyStore.getInstance(keyStoreProperties.getType());
            Resource resource = resourceLoader.getResource(keyStoreProperties.getLocation());

            try (InputStream inputStream = resource.getInputStream()) {
                keyStore.load(inputStream, toPasswordChars(keyStoreProperties.getStorePassword()));
            }

            Key key = keyStore.getKey(
                    keyStoreProperties.getKeyAlias(),
                    toPasswordChars(keyStoreProperties.getKeyPassword())
            );

            if (!(key instanceof PrivateKey privateKey)) {
                throw new IllegalStateException("JWT 私钥库中指定别名不是私钥条目：" + keyStoreProperties.getKeyAlias());
            }

            if (!(privateKey instanceof RSAPrivateKey rsaPrivateKey)) {
                throw new IllegalStateException("JWT 私钥不是 RSA 私钥，当前工具只支持 RSA 签名");
            }

            return rsaPrivateKey;
        } catch (Exception exception) {
            throw new IllegalStateException("加载 JWT 私钥失败，请检查私钥库位置、密码和别名配置", exception);
        }
    }

    /**
     * 从私钥库中的证书条目读取公钥，供 user-service 自身校验退出请求使用。
     */
    private RSAPublicKey loadPublicKey() {
        UserJwtProperties.PrivateKeyStore keyStoreProperties = jwtProperties.getPrivateKeyStore();

        try {
            KeyStore keyStore = KeyStore.getInstance(keyStoreProperties.getType());
            Resource resource = resourceLoader.getResource(keyStoreProperties.getLocation());

            try (InputStream inputStream = resource.getInputStream()) {
                keyStore.load(inputStream, toPasswordChars(keyStoreProperties.getStorePassword()));
            }

            Certificate certificate = keyStore.getCertificate(keyStoreProperties.getKeyAlias());
            if (certificate == null) {
                throw new IllegalStateException("JWT 私钥库中未找到证书条目：" + keyStoreProperties.getKeyAlias());
            }

            PublicKey publicKey = certificate.getPublicKey();
            if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
                throw new IllegalStateException("JWT 证书中的公钥不是 RSA 公钥");
            }

            return rsaPublicKey;
        } catch (Exception exception) {
            throw new IllegalStateException("加载 JWT 公钥失败，请检查私钥库证书条目", exception);
        }
    }

    private String removeBearerPrefix(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new IllegalArgumentException("请求头中缺少 Authorization");
        }

        if (authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        }

        return authorizationHeader.trim();
    }

    private char[] toPasswordChars(String password) {
        return password == null ? new char[0] : password.toCharArray();
    }
}
