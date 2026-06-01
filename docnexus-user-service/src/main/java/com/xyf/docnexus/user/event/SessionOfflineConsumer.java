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
 * 用户会话离线事件消费者。
 *
 * <p>离线事件只更新 online_status、offline_at 和 last_active_at。
 * 它不会把授权会话 status 改成 EXPIRED，因此用户下次仍可继续刷新登录态。</p>
 */
@Component
@RocketMQMessageListener(
        topic = "${docnexus.session-event.topic}",
        consumerGroup = "${docnexus.session-event.consumer-group}-offline",
        selectorExpression = "SESSION_OFFLINE"
)
public class SessionOfflineConsumer implements RocketMQListener<SessionOfflineEvent> {

    private static final Logger log = LoggerFactory.getLogger(SessionOfflineConsumer.class);

    private final SessionMapper sessionMapper;

    public SessionOfflineConsumer(SessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    /**
     * 消费离线事件，并幂等更新 MySQL 中的展示状态。
     */
    @Override
    public void onMessage(SessionOfflineEvent event) {
        validateEvent(event);

        UserSessionLogoutParam param = new UserSessionLogoutParam();
        param.setSessionId(event.getSessionId());
        param.setLogoutAt(event.getOfflineAt());
        param.setLastActiveAt(toLocalDateTime(event.getLastActiveAtMillis()));
        param.setUpdateTime(event.getOfflineAt());

        int rows = sessionMapper.markOfflineBySessionId(param);

        log.info("用户会话离线事件消费完成，eventId={}, sessionId={}, rows={}",
                event.getEventId(), event.getSessionId(), rows);
    }

    /**
     * 校验离线事件必要字段。
     */
    private void validateEvent(SessionOfflineEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("会话离线事件不能为空");
        }
        if (!StringUtils.hasText(event.getEventId())) {
            throw new IllegalArgumentException("会话离线事件缺少 eventId");
        }
        if (!StringUtils.hasText(event.getSessionId())) {
            throw new IllegalArgumentException("会话离线事件缺少 sessionId");
        }
        if (event.getOfflineAt() == null) {
            event.setOfflineAt(LocalDateTime.now());
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
