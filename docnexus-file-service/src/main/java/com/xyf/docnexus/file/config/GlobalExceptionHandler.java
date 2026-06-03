package com.xyf.docnexus.file.config;

import com.xyf.docnexus.common.VO.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 文件服务统一异常处理器。
 *
 * <p>把参数错误和业务失败转换为统一响应，避免前端收到裸 500。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务参数异常。
     */
    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ApiResponse<Void> handleBadRequest(Exception exception) {
        return ApiResponse.badRequest(exception.getMessage());
    }

    /**
     * 处理请求体校验异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("请求参数不合法");
        return ApiResponse.badRequest(message);
    }

    /**
     * 处理缺少网关身份头的请求。
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ApiResponse<Void> handleMissingHeader(MissingRequestHeaderException exception) {
        return ApiResponse.unauthorized("缺少登录用户身份，请重新登录");
    }

    /**
     * 处理未预期异常。
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("文件服务处理失败", exception);
        return ApiResponse.fail("文件服务处理失败，请稍后再试");
    }
}
