package com.xyf.docnexus.gateway.event;

import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.common.event.GatewayAuditEvent;
import com.xyf.docnexus.common.event.SecurityAlertEvent;
import com.xyf.docnexus.gateway.config.GatewayAuditProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Gateway 事件发布器。
 *
 * <p>网关不直接访问 MySQL；请求审计、安全告警、限流和降级事件统一通过 RocketMQ 发送给日志消费者异步落库。</p>
 */
@Slf4j
@Component
public class GatewayEventPublisher {

    private final GatewayAuditProperties properties;
    private final RocketMQTemplate rocketMQTemplate;

    public GatewayEventPublisher(GatewayAuditProperties properties,
                                 ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
        this.properties = properties;
        this.rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
    }

    /**
     * 异步发送普通请求审计事件。
     */
    public void publishAudit(GatewayAuditEvent event) {
        if (!isEnabled()) {
            return;
        }
        String destination = properties.getTopic() + ":" + MqTopicConstants.TAG_REQUEST_AUDIT;
        try {
            rocketMQTemplate.asyncSend(destination, event, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("Gateway 请求审计事件发送成功，eventId={}", event.getEventId());
                }

                @Override
                public void onException(Throwable throwable) {
                    log.warn("Gateway 请求审计事件发送失败，eventId={}", event.getEventId(), throwable);
                }
            });
        } catch (Exception exception) {
            log.warn("Gateway 请求审计事件提交发送失败，eventId={}", event.getEventId(), exception);
        }
    }

    /**
     * 发送安全告警事件。
     *
     * <p>安全告警价值更高，优先同步发送；MQ 不可用时写本地安全日志兜底。</p>
     */
    public void publishSecurityAlert(String tag, SecurityAlertEvent event) {
        if (!isEnabled()) {
            return;
        }
        String destination = properties.getTopic() + ":" + tag;
        try {
            rocketMQTemplate.syncSend(destination, event);
        } catch (Exception exception) {
            log.warn("Gateway 安全告警事件发送失败，eventId={}, alertType={}, path={}",
                    event.getEventId(), event.getAlertType(), event.getPath(), exception);
        }
    }

    /**
     * 判断事件发布是否可用。
     */
    private boolean isEnabled() {
        return Boolean.TRUE.equals(properties.getEnabled()) && rocketMQTemplate != null;
    }
}
