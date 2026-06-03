package com.xyf.docnexus.common.constant;

/**
 * RocketMQ Topic 和 Tag 常量。
 *
 * <p>所有服务统一从公共模块引用，避免各服务硬编码 Topic 名称导致后续迁移困难。</p>
 */
public final class MqTopicConstant {

    public static final String USER_EVENT_TOPIC = "docnexus_user_event";
    public static final String GATEWAY_EVENT_TOPIC = "docnexus_gateway_event";
    public static final String FILE_EVENT_TOPIC = "docnexus_file_event";
    public static final String LOG_EVENT_TOPIC = "docnexus_log_event";

    public static final String TAG_REQUEST_AUDIT = "REQUEST_AUDIT";
    public static final String TAG_SECURITY_ALERT = "SECURITY_ALERT";
    public static final String TAG_RATE_LIMITED = "RATE_LIMITED";
    public static final String TAG_SENTINEL_BLOCK = "SENTINEL_BLOCK";

    public static final String TAG_USER_REGISTERED = "USER_REGISTERED";
    public static final String TAG_PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String TAG_USER_PROFILE_UPDATED = "USER_PROFILE_UPDATED";
    public static final String TAG_SESSION_EXPIRED = "SESSION_EXPIRED";
    public static final String TAG_SESSION_OFFLINE = "SESSION_OFFLINE";
    public static final String TAG_PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String TAG_TOKEN_VERSION_CHANGED = "TOKEN_VERSION_CHANGED";

    public static final String TAG_FILE_UPLOAD_STARTED = "FILE_UPLOAD_STARTED";
    public static final String TAG_FILE_UPLOADED = "FILE_UPLOADED";
    public static final String TAG_FILE_UPLOAD_FAILED = "FILE_UPLOAD_FAILED";
    public static final String TAG_FILE_DELETED = "FILE_DELETED";
    public static final String TAG_FILE_PARSE_TASK_CREATED = "FILE_PARSE_TASK_CREATED";

    public static final String TAG_BUSINESS_OPERATION_LOG = "BUSINESS_OPERATION_LOG";

    private MqTopicConstant() {
    }
}
