package com.xyf.docnexuslogservice.mapper;

import com.xyf.docnexuslogservice.entity.MqConsumeLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * MQ 消费幂等与耗时 Mapper。
 */
public interface MqConsumeLogMapper {

    /**
     * 抢占消费处理权；已成功消费的消息不会被重复处理，失败消息允许 RocketMQ 重试时再次进入 PROCESSING。
     */
    int claimProcessing(MqConsumeLog log);

    /**
     * 消费成功后记录完成时间和耗时。
     */
    int markSuccess(@Param("eventId") String eventId,
                    @Param("consumerGroup") String consumerGroup,
                    @Param("consumeFinishedAt") LocalDateTime consumeFinishedAt,
                    @Param("durationMs") Long durationMs);

    /**
     * 消费失败后记录错误和失败前耗时，随后由 Consumer 抛异常交给 RocketMQ 重试。
     */
    int markFailed(@Param("eventId") String eventId,
                   @Param("consumerGroup") String consumerGroup,
                   @Param("consumeFinishedAt") LocalDateTime consumeFinishedAt,
                   @Param("durationMs") Long durationMs,
                   @Param("errorMessage") String errorMessage);
}
