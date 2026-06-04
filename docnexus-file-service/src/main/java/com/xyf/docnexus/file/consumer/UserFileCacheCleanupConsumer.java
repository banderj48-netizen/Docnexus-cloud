package com.xyf.docnexus.file.consumer;

import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.common.log.BusinessOperationLog;
import com.xyf.docnexus.file.dto.UserSessionOfflineEvent;
import com.xyf.docnexus.file.service.FileCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用户全部会话离线后的文件缓存清理消费者。
 *
 * <p>只在 user-service 判定 allSessionsOffline=true 时清理缓存；
 * 单个会话离线不会影响同一用户其他在线设备的文件缓存。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopicConstants.USER_EVENT_TOPIC,
        consumerGroup = "docnexus-file-user-cache-cleanup-group",
        selectorExpression = MqTopicConstants.TAG_SESSION_OFFLINE
)
public class UserFileCacheCleanupConsumer implements RocketMQListener<UserSessionOfflineEvent> {

    private final FileCacheService fileCacheService;

    /**
     * 消费用户离线事件，并在用户所有会话均离线时原子清理文件缓存。
     */
    @Override
    @BusinessOperationLog(module = "文件服务", functionName = "用户离线缓存清理", operationType = "MQ_CONSUME",
            operationName = "消费用户全离线事件清理文件缓存", triggerType = "MQ_CONSUME", operationSource = "ROCKETMQ", userVisible = false)
    public void onMessage(UserSessionOfflineEvent event) {
        if (event == null || event.getUserId() == null || !StringUtils.hasText(event.getSessionId())) {
            log.warn("用户离线缓存清理事件缺少关键字段，event={}", event);
            return;
        }
        if (!Boolean.TRUE.equals(event.getAllSessionsOffline())) {
            log.debug("用户仍存在在线会话，跳过文件缓存清理，userId={}, sessionId={}, eventId={}",
                    event.getUserId(), event.getSessionId(), event.getEventId());
            return;
        }
        long deleted = fileCacheService.clearUserCaches(event.getUserId());
        log.info("用户全部会话离线，已原子清理文件服务 Redis 缓存，userId={}, sessionId={}, eventId={}, deletedKeys={}",
                event.getUserId(), event.getSessionId(), event.getEventId(), deleted);
    }
}
