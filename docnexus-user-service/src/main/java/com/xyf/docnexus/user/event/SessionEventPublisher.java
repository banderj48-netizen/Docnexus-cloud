package com.xyf.docnexus.user.event;

import com.xyf.docnexus.user.config.SessionEventProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import static com.xyf.docnexus.user.constant.UserSessionConstants.MQ_TAG_SESSION_EXPIRED;
import static com.xyf.docnexus.user.constant.UserSessionConstants.MQ_TAG_SESSION_OFFLINE;

/**
 * 用户会话事件发送器。
 *
 * <p>业务层只依赖该类发送事件，不直接依赖 RocketMQTemplate，
 * 后续如果切换 MQ 实现，可以把影响范围限制在事件层。</p>
 */
@Component
public class SessionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SessionEventPublisher.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final SessionEventProperties sessionEventProperties;

    public SessionEventPublisher(RocketMQTemplate rocketMQTemplate,
                                 SessionEventProperties sessionEventProperties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.sessionEventProperties = sessionEventProperties;
    }

    /**
     * 发送会话失效事件。
     *
     * <p>退出、过期、改密码等安全动作必须确认事件进入 MQ。
     * 如果 MQ 不可用，业务层会同步落库兜底。</p>
     */
    public void publishSessionExpired(SessionExpiredEvent event) {
        String destination = sessionEventProperties.getTopic() + ":" + MQ_TAG_SESSION_EXPIRED;
        Message<SessionExpiredEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(RocketMQHeaders.KEYS, event.getSessionId())
                .build();

        rocketMQTemplate.syncSend(destination, message);

        log.info("用户会话失效事件发送成功，eventId={}, sessionId={}, reason={}",
                event.getEventId(), event.getSessionId(), event.getCloseReason());
    }

    /**
     * 发送会话离线事件。
     *
     * <p>离线事件只用于异步更新 MySQL 展示状态，不参与实时鉴权。
     * 发送失败时调用方只记录日志，不阻塞 heartbeat 清理任务。</p>
     */
    public void publishSessionOffline(SessionOfflineEvent event) {
        String destination = sessionEventProperties.getTopic() + ":" + MQ_TAG_SESSION_OFFLINE;
        Message<SessionOfflineEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(RocketMQHeaders.KEYS, event.getSessionId())
                .build();

        rocketMQTemplate.syncSend(destination, message);

        log.info("用户会话离线事件发送成功，eventId={}, sessionId={}, lastActiveAtMillis={}",
                event.getEventId(), event.getSessionId(), event.getLastActiveAtMillis());
    }
}
