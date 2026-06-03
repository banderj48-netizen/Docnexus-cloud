package com.xyf.docnexus.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Gateway 审计与安全告警事件配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "docnexus.gateway.audit")
public class GatewayAuditProperties {

    /**
     * 是否启用网关审计事件发送。
     */
    private Boolean enabled = true;

    /**
     * Gateway 事件 Topic。
     */
    private String topic = "docnexus_gateway_event";

    /**
     * RocketMQ producer group。
     */
    private String producerGroup = "docnexus-gateway-audit-producer-group";
}
