package com.xyf.docnexus.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * user-service 的 JWT 配置读取类。
 *
 * <p>这里专门读取 application.yml 中 docnexus.jwt 开头的配置。
 * user-service 负责“签发令牌”，所以它需要读取私钥库的位置、密码、别名等信息，
 * 然后由 JwtSignTool 从密钥库中加载 RSA 私钥并完成 JWT 签名。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "docnexus.jwt")
public class UserJwtProperties {

    /**
     * JWT 签发方。
     *
     * <p>签发方会写入令牌的 iss 字段，gateway-service 校验时也会校验这个值。
     * 这样可以避免其他系统签发的令牌被误认为是 DocNexus 自己的令牌。</p>
     */
    private String issuer;

    /**
     * 当前 JWT 签名密钥 ID。
     *
     * <p>多实例部署时，所有 user-service 实例必须使用同一个 active keyId 和同一份受管私钥。
     * Gateway 会根据 JWT Header 中的 kid 选择对应公钥验签。</p>
     */
    private String keyId = "docnexus-rsa-2026-01";

    /**
     * JWT 有效期，单位：秒。
     *
     * <p>user-service 签发令牌时会用当前时间加上该秒数生成过期时间 exp。</p>
     */
    private Long expireSeconds;

    /**
     * JWT 黑名单 txt 文件位置。
     *
     * <p>当前用于开发阶段的严谨退出登录：用户退出后将当前 JWT 的 jti 写入该文件，
     * gateway-service 后续校验请求时也读取同一个文件。</p>
     */
    private String blacklistLocation;

    /**
     * refreshToken 有效期，单位：秒。
     *
     * <p>refreshToken 用于 accessToken 过期后的续签，通常应明显长于 accessToken。
     * 该字段对应配置：docnexus.jwt.refresh-token-expire-seconds。</p>
     */
    private Long refreshTokenExpireSeconds;

    /**
     * RSA 私钥库配置。
     *
     * <p>私钥只放在 user-service，gateway-service 不应该持有私钥。</p>
     */
    private PrivateKeyStore privateKeyStore = new PrivateKeyStore();

    /**
     * JWKS 内部接口配置。
     *
     * <p>该接口只暴露公钥材料，供 Gateway 拉取和缓存，不暴露私钥。</p>
     */
    private Jwks jwks = new Jwks();

    @Data
    public static class Jwks {
        private Boolean enabled = true;
    }

    @Data
    public static class PrivateKeyStore {

        /**
         * 私钥库文件位置。
         *
         * <p>开发环境通常使用 classpath:jwt-private.p12，
         * 生产环境建议通过环境变量指向服务器上的安全路径。</p>
         */
        private String location;

        /**
         * 密钥库类型。
         *
         * <p>当前使用 keytool 生成的 PKCS12 格式，所以默认值应为 PKCS12。</p>
         */
        private String type;

        /**
         * 打开密钥库文件需要的密码。
         */
        private String storePassword;

        /**
         * 私钥在密钥库中的别名。
         */
        private String keyAlias;

        /**
         * 读取私钥条目需要的密码。
         *
         * <p>PKCS12 通常不支持独立的 keypass，实际使用时一般和 storePassword 保持一致。</p>
         */
        private String keyPassword;
    }
}
