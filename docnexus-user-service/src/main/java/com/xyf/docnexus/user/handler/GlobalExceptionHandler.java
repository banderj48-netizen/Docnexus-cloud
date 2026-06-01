package com.xyf.docnexus.user.handler;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.xyf.docnexus.common.VO.ApiResponse;
import com.xyf.docnexus.common.constant.ResponseCode;
import com.xyf.docnexus.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * user-service 统一异常处理器。
 *
 * <p>目标：
 * 1. Controller 和 Service 不再把异常直接暴露给前端；
 * 2. 所有业务失败都统一转换为 ApiResponse；
 * 3. 系统异常记录详细日志，但返回给前端时只给出安全提示，避免泄露堆栈、SQL、Redis key 等内部信息。</p>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.xyf.docnexus.user")
public class GlobalExceptionHandler {

    /**
     * 处理明确的业务异常。
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        log.warn("业务处理失败，code={}, message={}", exception.getCode(), exception.getMessage());
        return ApiResponse.fail(exception.getCode(), exception.getMessage());
    }

    /**
     * 处理数据库唯一键冲突。
     *
     * <p>注册时即使前面已经查过用户名，高并发下仍可能由数据库唯一索引兜底触发该异常。</p>
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResponse<Void> handleDuplicateKeyException(DuplicateKeyException exception) {
        log.warn("数据库唯一键冲突", exception);
        return ApiResponse.fail(ResponseCode.DATA_ALREADY_EXISTS, "数据已存在，请检查后重试");
    }

    /**
     * 处理缺失请求头。
     *
     * <p>例如用户资料接口必须由网关注入 X-User-Id，如果缺失，说明请求没有经过正确鉴权链路。</p>
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ApiResponse<Void> handleMissingRequestHeaderException(MissingRequestHeaderException exception) {
        log.warn("请求头缺失，header={}", exception.getHeaderName());
        return ApiResponse.unauthorized("登录状态无效，请重新登录");
    }

    /**
     * 处理参数类型不匹配。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatchException(MethodArgumentTypeMismatchException exception) {
        log.warn("请求参数类型错误，name={}, value={}", exception.getName(), exception.getValue());
        return ApiResponse.badRequest("请求参数格式不正确");
    }

    /**
     * 处理 JSON 请求体解析失败。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof InvalidFormatException invalidFormatException) {
            log.warn("请求 JSON 字段格式错误，path={}", invalidFormatException.getPathReference());
        } else {
            log.warn("请求 JSON 解析失败", exception);
        }
        return ApiResponse.badRequest("请求参数格式不正确");
    }

    /**
     * 处理 IllegalArgumentException。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException exception) {
        log.warn("请求参数不合法，message={}", exception.getMessage());
        return ApiResponse.badRequest(exception.getMessage());
    }

    /**
     * 兼容当前项目里已有的 RuntimeException 业务抛错。
     *
     * <p>后续建议逐步把 RuntimeException 替换为 BusinessException，让错误码更明确。</p>
     */
    @ExceptionHandler(RuntimeException.class)
    public ApiResponse<Void> handleRuntimeException(RuntimeException exception) {
        log.warn("业务运行异常，message={}", exception.getMessage(), exception);
        return ApiResponse.fail(ResponseCode.BUSINESS_ERROR, exception.getMessage());
    }

    /**
     * 兜底系统异常。
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("服务内部异常", exception);
        return ApiResponse.fail(ResponseCode.INTERNAL_ERROR, "服务内部异常，请稍后再试");
    }
}
