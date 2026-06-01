package com.xyf.docnexus.user.event;

import com.xyf.docnexus.user.Mapper.SessionMapper;
import com.xyf.docnexus.user.entity.UserSessionLogoutParam;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 用户会话失效事件消费者。
 *
 * <p>Redis 已经在请求线程里完成实时失效，该 Consumer 只负责最终更新 MySQL。
 * SQL 需要幂等，重复消费时 rows=0 也视为成功。</p>
 */
@Component
@RocketMQMessageListener(
        topic = "${docnexus.session-event.topic}",
        consumerGroup = "${docnexus.session-event.consumer-group}",
        selectorExpression = "SESSION_EXPIRED"
)
public class SessionExpiredConsumer implements RocketMQListener<SessionExpiredEvent> {

    private static final Logger log = LoggerFactory.getLogger(SessionExpiredConsumer.class);

    private final SessionMapper sessionMapper;

    public SessionExpiredConsumer(SessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    /**
     * 消费会话失效事件，并幂等更新 MySQL。
     */
    @Override
    public void onMessage(SessionExpiredEvent event) {
        validateEvent(event);

        UserSessionLogoutParam param = new UserSessionLogoutParam();
        param.setUserId(event.getUserId());
        param.setSessionId(event.getSessionId());
        param.setAccessJti(event.getAccessJti());
        param.setStatus("EXPIRED");
        param.setCloseReason(event.getCloseReason());
        param.setExpiredAt(event.getOccurredAt());
        param.setLogoutAt("LOGOUT".equals(event.getCloseReason()) ? event.getOccurredAt() : null);
        param.setLastActiveAt(toLocalDateTime(event.getLastActiveAtMillis()));
        param.setUpdateTime(event.getOccurredAt());

        int rows = sessionMapper.expireBySessionId(param);

        log.info("用户会话失效事件消费完成，eventId={}, sessionId={}, reason={}, rows={}",
                event.getEventId(), event.getSessionId(), event.getCloseReason(), rows);
    }

    /**
     * 校验 MQ 消息是否包含必要字段。
     */
    private void validateEvent(SessionExpiredEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("会话失效事件不能为空");
        }
        if (!StringUtils.hasText(event.getEventId())) {
            throw new IllegalArgumentException("会话失效事件缺少 eventId");
        }
        if (event.getUserId() == null || event.getUserId() <= 0) {
            throw new IllegalArgumentException("会话失效事件 userId 非法");
        }
        if (!StringUtils.hasText(event.getSessionId())) {
            throw new IllegalArgumentException("会话失效事件缺少 sessionId");
        }
        if (!StringUtils.hasText(event.getCloseReason())) {
            throw new IllegalArgumentException("会话失效事件缺少 closeReason");
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(LocalDateTime.now());
        }
    }

    /**
     * 将毫秒时间戳转换为 LocalDateTime。
     */
    private LocalDateTime toLocalDateTime(Long epochMillis) {
        if (epochMillis == null || epochMillis <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }
}
