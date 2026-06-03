package com.xyf.docnexus.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Gateway Sentinel 配置属性。
 *
 * <p>这里保留业务开关和控制台地址，真正的 Sentinel starter 也会读取 spring.cloud.sentinel 标准配置。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "docnexus.gateway.sentinel")
public class GatewaySentinelProperties {

    /**
     * 是否启用 Sentinel 网关阻断处理。
     */
    private Boolean enabled = true;

    /**
     * Sentinel Dashboard 地址。
     */
    private String dashboard = "127.0.0.1:8858";

    /**
     * Sentinel transport 端口。
     */
    private String transportPort = "8719";
}
