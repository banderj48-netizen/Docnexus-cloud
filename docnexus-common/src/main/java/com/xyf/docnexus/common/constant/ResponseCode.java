package com.xyf.docnexus.common.constant;

/**
 * 统一业务状态码常量。
 *
 * 注意：
 * 这里的 code 是业务响应码，不完全等同于 HTTP 状态码。
 * Controller 通常仍然返回 HTTP 200，
 * 前端根据 ApiResponse.code 判断业务是否成功。
 */
public final class ResponseCode {

    private ResponseCode() {
        // 工具常量类不允许被实例化
    }

    /**
     * 请求成功。
     */
    public static final int SUCCESS = 200;

    /**
     * 请求参数错误。
     */
    public static final int BAD_REQUEST = 400;

    /**
     * 未登录或登录已过期。
     */
    public static final int UNAUTHORIZED = 401;

    /**
     * 已登录，但没有权限访问。
     */
    public static final int FORBIDDEN = 403;

    /**
     * 请求过于频繁。
     *
     * 主要用于登录限流、短信验证码限流、接口防刷等场景。
     */
    public static final int TOO_MANY_REQUESTS = 429;

    /**
     * 请求的资源不存在。
     */
    public static final int NOT_FOUND = 404;

    /**
     * 业务处理失败。
     *
     * 例如用户名或密码错误、文件状态不允许操作等。
     */
    public static final int BUSINESS_ERROR = 1001;

    /**
     * 数据不存在。
     */
    public static final int DATA_NOT_FOUND = 1002;

    /**
     * 数据已存在。
     */
    public static final int DATA_ALREADY_EXISTS = 1003;

    /**
     * 服务端内部异常。
     */
    public static final int INTERNAL_ERROR = 500;

    /**
     * 下游服务调用失败。
     *
     * 例如 user-service 调用 Python AI 服务失败。
     */
    public static final int REMOTE_SERVICE_ERROR = 2001;

    /**
     * Nacos 或服务发现异常。
     */
    public static final int SERVICE_DISCOVERY_ERROR = 2002;

    /**
     * 文件上传失败。
     */
    public static final int FILE_UPLOAD_ERROR = 3001;

    /**
     * 文件下载失败。
     */
    public static final int FILE_DOWNLOAD_ERROR = 3002;

    /**
     * AI 任务提交失败。
     */
    public static final int AI_TASK_SUBMIT_ERROR = 4001;

    /**
     * AI 任务执行失败。
     */
    public static final int AI_TASK_EXECUTE_ERROR = 4002;
}
