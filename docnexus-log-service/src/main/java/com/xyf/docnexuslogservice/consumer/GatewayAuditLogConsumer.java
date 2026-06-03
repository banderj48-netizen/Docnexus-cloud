package com.xyf.docnexuslogservice.consumer;

import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.common.event.GatewayAuditEvent;
import com.xyf.docnexuslogservice.service.GatewayLogIngestService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gateway 请求审计事件消费者。
 */
@Component
@RocketMQMessageListener(
        topic = "${docnexus.log.gateway-topic:docnexus_gateway_event}",
        consumerGroup = "${docnexus.log.audit-consumer-group:docnexus-log-gateway-audit-consumer-group}",
        selectorExpression = MqTopicConstants.TAG_REQUEST_AUDIT,
        maxReconsumeTimes = 3
)
public class GatewayAuditLogConsumer implements RocketMQListener<GatewayAuditEvent> {

    private final GatewayLogIngestService ingestService;
    private final String topic;
    private final String consumerGroup;

    public GatewayAuditLogConsumer(GatewayLogIngestService ingestService,
                                   @Value("${docnexus.log.gateway-topic:docnexus_gateway_event}") String topic,
                                   @Value("${docnexus.log.audit-consumer-group:docnexus-log-gateway-audit-consumer-group}") String consumerGroup) {
        this.ingestService = ingestService;
        this.topic = topic;
        this.consumerGroup = consumerGroup;
    }

    /**
     * 消费 Gateway REQUEST_AUDIT 事件并落库。
     */
    @Override
    public void onMessage(GatewayAuditEvent event) {
        ingestService.ingestAudit(event, topic, MqTopicConstants.TAG_REQUEST_AUDIT, consumerGroup);
    }
}
