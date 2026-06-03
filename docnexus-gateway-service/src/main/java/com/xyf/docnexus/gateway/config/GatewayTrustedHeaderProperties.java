package com.xyf.docnexus.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Gateway 注入可信请求头的签名配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "docnexus.gateway.trusted-header")
public class GatewayTrustedHeaderProperties {

    /**
     * 网关和下游服务之间共享的内部签名密钥。
     *
     * <p>生产环境必须通过环境变量、Secret 或配置中心注入，不要使用默认值。</p>
     */
    private String signSecret = "docnexus-dev-gateway-sign-secret";
}
