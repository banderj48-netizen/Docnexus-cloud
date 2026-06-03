package com.xyf.docnexuslogservice.service;

import com.xyf.docnexus.common.event.BusinessOperationLogEvent;
import com.xyf.docnexus.common.event.GatewayAuditEvent;
import com.xyf.docnexus.common.event.SecurityAlertEvent;

/**
 * 日志事件入库服务。
 */
public interface GatewayLogIngestService {

    /**
     * 消费并落库网关请求审计事件。
     */
    void ingestAudit(GatewayAuditEvent event, String topic, String tag, String consumerGroup);

    /**
     * 消费并落库安全告警事件。
     */
    void ingestSecurityAlert(SecurityAlertEvent event, String topic, String tag, String consumerGroup);

    /**
     * 消费并落库业务操作耗时事件。
     */
    void ingestBusinessOperation(BusinessOperationLogEvent event, String topic, String tag, String consumerGroup);
}
