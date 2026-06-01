package com.xyf.docnexus.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * gateway-service 的 JWT 配置读取类。
 *
 * <p>gateway-service 只负责校验令牌，不负责签发令牌。
 * 因此这里读取的是公钥证书、签发方、时钟偏移容忍时间和白名单路径。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "docnexus.jwt")
public class GatewayJwtProperties {

    /**
     * 允许通过的签发方。
     *
     * <p>该值必须和 user-service 签发 JWT 时写入的 issuer 一致。</p>
     */
    private String issuer;

    /**
     * 时钟偏移容忍时间，单位：秒。
     *
     * <p>分布式部署时，不同机器的系统时间可能存在少量误差。
     * 设置该值后，令牌刚签发或刚过期的一小段时间内不会因为机器时间误差被误判。</p>
     */
    private Long clockSkewSeconds = 60L;


    /**
     * 不需要登录即可访问的路径。
     *
     * <p>例如登录、注册、健康检查等接口必须放行，否则用户还没拿到令牌就无法访问登录接口。</p>
     */
    private List<String> whiteList = new ArrayList<>();

    /**
     * RSA 公钥证书配置。
     */
    private PublicCert publicCert = new PublicCert();

    @Data
    public static class PublicCert {

        /**
         * 公钥证书位置。
         *
         * <p>开发环境通常放在 classpath:jwt-public.cer；
         * 生产环境可以通过环境变量指向统一下发的证书文件。</p>
         */
        private String location;
    }
}
