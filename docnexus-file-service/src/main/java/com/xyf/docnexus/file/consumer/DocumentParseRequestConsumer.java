package com.xyf.docnexus.file.consumer;

import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.common.log.BusinessOperationLog;
import com.xyf.docnexus.file.client.AiDocumentParseClient;
import com.xyf.docnexus.file.dto.AiDocumentParseRequest;
import com.xyf.docnexus.file.dto.AiDocumentParseResponse;
import com.xyf.docnexus.file.dto.DocumentReparseEvent;
import com.xyf.docnexus.file.entity.DocumentFile;
import com.xyf.docnexus.file.mapper.DocumentFileMapper;
import com.xyf.docnexus.file.service.DocumentFileLookupService;
import com.xyf.docnexus.file.service.FileCacheService;
import com.xyf.docnexus.file.util.FileRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 文档解析请求 MQ 消费者。
 *
 * <p>默认关闭；开启后按 RocketMQ 重试机制调用 AI 服务，失败抛异常进入重试，超过次数进入死信队列。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "docnexus.file.parse-consumer", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqTopicConstants.FILE_EVENT_TOPIC,
        selectorExpression = MqTopicConstants.TAG_DOCUMENT_REPARSE_REQUESTED,
        consumerGroup = "${docnexus.file.parse-consumer.group:docnexus-file-parse-consumer-group}",
        consumeMode = ConsumeMode.ORDERLY,
        maxReconsumeTimes = 3
)
public class DocumentParseRequestConsumer implements RocketMQListener<DocumentReparseEvent> {

    private final DocumentFileMapper documentFileMapper;
    private final FileCacheService fileCacheService;
    private final DocumentFileLookupService documentFileLookupService;
    private final AiDocumentParseClient aiDocumentParseClient;

    /**
     * 消费文档解析请求并调用 AI 服务。
     */
    @Override
    @BusinessOperationLog(module = "文件服务", functionName = "解析消息消费", operationType = "MQ_CONSUME",
            operationName = "消费文档解析请求", triggerType = "MQ_CONSUME", operationSource = "ROCKETMQ", userVisible = false)
    public void onMessage(DocumentReparseEvent event) {
        validateEvent(event);
        String lockKey = FileRedisKeys.parseConsumeLockKey(event.getUserId(), event.getFileId());
        String lockToken = fileCacheService.tryLock(lockKey, Duration.ofMinutes(30));
        if (lockToken == null) {
            throw new IllegalStateException("文档解析消费锁获取失败，等待 RocketMQ 重试");
        }
        try {
            int updatedRows = documentFileMapper.markParseProcessing(event.getUserId(), event.getFileId());
            if (updatedRows == 0) {
                log.info("文档解析任务已被处理或状态已变化，fileId={}, userId={}, eventId={}",
                        event.getFileId(), event.getUserId(), event.getEventId());
                return;
            }
            DocumentFile file = documentFileLookupService.requireFile(event.getUserId(), event.getFileId());
            file.setParseStatus("PROCESSING");
            documentFileLookupService.cacheFile(file);
            AiDocumentParseResponse response = aiDocumentParseClient.parseDocument(new AiDocumentParseRequest(
                    event.getEventId(),
                    event.getFileId(),
                    event.getUserId(),
                    event.getVersionNumber(),
                    event.getBucketName(),
                    event.getObjectKey(),
                    event.getReason()
            ));
            if (response == null || !Boolean.TRUE.equals(response.getAccepted())) {
                throw new IllegalStateException(response == null ? "AI 服务未返回解析接收结果" : response.getMessage());
            }
        } finally {
            fileCacheService.unlock(lockKey, lockToken);
        }
    }

    /**
     * 校验 MQ 事件关键字段，避免坏消息进入解析链路。
     */
    private void validateEvent(DocumentReparseEvent event) {
        if (event == null
                || !StringUtils.hasText(event.getEventId())
                || !StringUtils.hasText(event.getFileId())
                || event.getUserId() == null
                || !StringUtils.hasText(event.getBucketName())
                || !StringUtils.hasText(event.getObjectKey())) {
            throw new IllegalArgumentException("文档解析事件缺少必要字段");
        }
    }
}
