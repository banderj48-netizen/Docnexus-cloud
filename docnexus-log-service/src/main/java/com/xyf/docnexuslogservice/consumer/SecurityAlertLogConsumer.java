package com.xyf.docnexuslogservice.consumer;

import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.common.event.SecurityAlertEvent;
import com.xyf.docnexuslogservice.service.GatewayLogIngestService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gateway 安全告警事件消费者。
 */
@Component
@RocketMQMessageListener(
        topic = "${docnexus.log.gateway-topic:docnexus_gateway_event}",
        consumerGroup = "${docnexus.log.security-consumer-group:docnexus-log-security-alert-consumer-group}",
        selectorExpression = "SECURITY_ALERT || RATE_LIMITED || SENTINEL_BLOCK",
        maxReconsumeTimes = 3
)
public class SecurityAlertLogConsumer implements RocketMQListener<SecurityAlertEvent> {

    private final GatewayLogIngestService ingestService;
    private final String topic;
    private final String consumerGroup;

    public SecurityAlertLogConsumer(GatewayLogIngestService ingestService,
                                    @Value("${docnexus.log.gateway-topic:docnexus_gateway_event}") String topic,
                                    @Value("${docnexus.log.security-consumer-group:docnexus-log-security-alert-consumer-group}") String consumerGroup) {
        this.ingestService = ingestService;
        this.topic = topic;
        this.consumerGroup = consumerGroup;
    }

    /**
     * 消费 Gateway 安全告警类事件并落库。
     */
    @Override
    public void onMessage(SecurityAlertEvent event) {
        ingestService.ingestSecurityAlert(event, topic, event.getAlertType(), consumerGroup);
    }
}
