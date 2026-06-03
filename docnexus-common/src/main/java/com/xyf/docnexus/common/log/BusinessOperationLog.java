package com.xyf.docnexus.common.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务操作日志注解。
 *
 * <p>用户主动点击触发的接口优先标注在 Controller 方法上；MQ 消费、定时任务等后台入口可以标注在
 * Consumer 或后台任务方法上。普通用户前端只展示 triggerType=USER_ACTION 且 userVisible=true 的记录。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessOperationLog {

    /**
     * 业务模块名称，例如“用户中心”“文件服务”。
     */
    String module();

    /**
     * 业务功能名称，例如“修改资料”“上传文档”。
     */
    String functionName();

    /**
     * 操作类型，例如 UPDATE、UPLOAD、DELETE、QUERY、MQ_CONSUME。
     */
    String operationType();

    /**
     * 前端或管理后台展示的操作名称。
     */
    String operationName();

    /**
     * 触发类型：USER_ACTION、AUTO_QUERY、MQ_CONSUME、SYSTEM_TASK、INTERNAL_CALL。
     */
    String triggerType() default "USER_ACTION";

    /**
     * 操作来源：FRONTEND、GATEWAY、MQ、SCHEDULER、SERVICE_INTERNAL。
     */
    String operationSource() default "FRONTEND";

    /**
     * 是否允许普通用户日志页展示。
     */
    boolean userVisible() default false;
}
