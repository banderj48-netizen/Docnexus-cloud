package com.xyf.docnexus.common.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.xyf.docnexus.common.constant.ResponseCode.*;

/**
 * 统一接口响应结果。
 *
 * 所有 Controller 都建议返回 ApiResponse<T>，
 * 这样前端可以用统一结构处理成功、失败、提示信息和业务数据。
 *
 * @param <T> data 字段的数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /**
     * 业务状态码。
     *
     * 常用约定：
     * 200 成功
     * 400 请求参数错误
     * 401 未登录或登录过期
     * 403 无权限
     * 500 服务端异常
     */
    private Integer code;

    /**
     * 响应提示信息。
     */
    private String message;

    /**
     * 真正返回给前端的数据。
     */
    private T data;

    /**
     * 成功但不返回数据。
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(SUCCESS, "操作成功", null);
    }

    /**
     * 成功并返回数据。
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS, "操作成功", data);
    }

    /**
     * 成功并自定义提示信息和数据。
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(SUCCESS, message, data);
    }

    /**
     * 失败，只返回错误信息。
     */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(INTERNAL_ERROR, message, null);
    }

    /**
     * 失败，自定义状态码和错误信息。
     */
    public static <T> ApiResponse<T> fail(Integer code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /**
     * 参数错误。
     */
    public static <T> ApiResponse<T> badRequest(String message) {
        return new ApiResponse<>(BAD_REQUEST, message, null);
    }

    /**
     * 未登录或登录过期。
     */
    public static <T> ApiResponse<T> unauthorized(String message) {
        return new ApiResponse<>(UNAUTHORIZED, message, null);
    }

    /**
     * 无权限。
     */
    public static <T> ApiResponse<T> forbidden(String message) {
        return new ApiResponse<>(FORBIDDEN, message, null);
    }
}