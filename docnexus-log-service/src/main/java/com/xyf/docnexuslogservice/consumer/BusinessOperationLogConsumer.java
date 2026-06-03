package com.xyf.docnexuslogservice.consumer;

import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.common.event.BusinessOperationLogEvent;
import com.xyf.docnexuslogservice.service.GatewayLogIngestService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 业务操作耗时日志消费者。
 */
@Component
@RocketMQMessageListener(
        topic = "${docnexus.log.business-topic:docnexus_log_event}",
        consumerGroup = "${docnexus.log.business-consumer-group:docnexus-log-business-operation-consumer-group}",
        selectorExpression = MqTopicConstants.TAG_BUSINESS_OPERATION_LOG,
        maxReconsumeTimes = 3
)
public class BusinessOperationLogConsumer implements RocketMQListener<BusinessOperationLogEvent> {

    private final GatewayLogIngestService ingestService;
    private final String topic;
    private final String consumerGroup;

    public BusinessOperationLogConsumer(GatewayLogIngestService ingestService,
                                        @Value("${docnexus.log.business-topic:docnexus_log_event}") String topic,
                                        @Value("${docnexus.log.business-consumer-group:docnexus-log-business-operation-consumer-group}") String consumerGroup) {
        this.ingestService = ingestService;
        this.topic = topic;
        this.consumerGroup = consumerGroup;
    }

    /**
     * 消费业务操作日志事件并落库。
     */
    @Override
    public void onMessage(BusinessOperationLogEvent event) {
        ingestService.ingestBusinessOperation(
                event,
                topic,
                MqTopicConstants.TAG_BUSINESS_OPERATION_LOG,
                consumerGroup
        );
    }
}
