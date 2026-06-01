package com.xyf.docnexus.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 用户会话事件配置。
 *
 * <p>用于读取 application.yml 中 docnexus.session-event 开头的配置。
 * 这样 Topic 和 ConsumerGroup 不写死在代码中，后续可以通过环境变量或 Nacos 配置中心调整。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "docnexus.session-event")
public class SessionEventProperties {

    /**
     * 用户会话事件 Topic。
     *
     * <p>当前第一阶段只发送 SESSION_EXPIRED 事件，
     * 后续如果需要离线事件，也可以复用该 Topic，通过 Tag 区分。</p>
     */
    private String topic;

    /**
     * 用户会话事件消费者组。
     *
     * <p>同一消费者组内多个 user-service 实例会共同消费消息，
     * 可以支撑后续多实例横向扩展。</p>
     */
    private String consumerGroup;
}