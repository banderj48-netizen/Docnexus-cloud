package com.xyf.docnexus.file.consumer;

import com.xyf.docnexus.common.log.BusinessOperationLog;
import com.xyf.docnexus.file.dto.DocumentReparseEvent;
import com.xyf.docnexus.file.entity.DocumentFile;
import com.xyf.docnexus.file.mapper.DocumentFileMapper;
import com.xyf.docnexus.file.service.DocumentFileLookupService;
import com.xyf.docnexus.file.service.FileCacheService;
import com.xyf.docnexus.file.util.FileRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 文档解析死信队列消费者。
 *
 * <p>解析请求超过 RocketMQ 最大重试次数后进入死信队列，本消费者负责把文件标记为解析失败。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "docnexus.file.parse-consumer", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${docnexus.file.parse-consumer.dead-letter-topic:%DLQ%docnexus-file-parse-consumer-group}",
        consumerGroup = "${docnexus.file.parse-consumer.dead-letter-group:docnexus-file-parse-dlq-consumer-group}"
)
public class DocumentParseDeadLetterConsumer implements RocketMQListener<DocumentReparseEvent> {

    private final DocumentFileMapper documentFileMapper;
    private final FileCacheService fileCacheService;
    private final DocumentFileLookupService documentFileLookupService;

    /**
     * 消费死信消息并标记文档解析失败。
     */
    @Override
    @BusinessOperationLog(module = "文件服务", functionName = "解析死信消费", operationType = "MQ_CONSUME",
            operationName = "处理文档解析死信", triggerType = "MQ_CONSUME", operationSource = "ROCKETMQ", userVisible = false)
    public void onMessage(DocumentReparseEvent event) {
        if (event == null || event.getUserId() == null || event.getFileId() == null) {
            log.warn("文档解析死信消息缺少关键字段，event={}", event);
            return;
        }
        String lockKey = FileRedisKeys.parseConsumeLockKey(event.getUserId(), event.getFileId());
        String lockToken = fileCacheService.tryLock(lockKey, Duration.ofMinutes(5));
        if (lockToken == null) {
            log.warn("文档解析死信锁获取失败，fileId={}, eventId={}", event.getFileId(), event.getEventId());
            return;
        }
        try {
            DocumentFile file = resolveFile(event);
            if (file == null) {
                return;
            }
            documentFileMapper.updateParseResult(
                    event.getUserId(),
                    event.getFileId(),
                    "FAILED",
                    "NONE",
                    "NONE",
                    null,
                    null,
                    "解析请求多次失败，请稍后再试"
            );
            fileCacheService.increaseVersion(event.getUserId(), file.getKnowledgeBaseId());
            file.setParseStatus("FAILED");
            file.setIndexStatus("NONE");
            file.setGraphStatus("NONE");
            file.setSummary(null);
            file.setKeywordsJson(null);
            file.setErrorMessage("解析请求多次失败，请稍后再试");
            documentFileLookupService.cacheFile(file);
        } finally {
            fileCacheService.unlock(lockKey, lockToken);
        }
    }

    /**
     * 查询文件元数据；文件已删除时按幂等成功跳过。
     */
    private DocumentFile resolveFile(DocumentReparseEvent event) {
        try {
            return documentFileLookupService.requireFile(event.getUserId(), event.getFileId());
        } catch (IllegalArgumentException exception) {
            log.info("文档解析死信对应文件不存在或已删除，跳过处理，fileId={}, userId={}, eventId={}",
                    event.getFileId(), event.getUserId(), event.getEventId());
            return null;
        }
    }
}
