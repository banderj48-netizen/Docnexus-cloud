package com.xyf.docnexus.common.exception;

import com.xyf.docnexus.common.constant.ResponseCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>用于明确表达“这是可预期的业务失败”，例如：
 * 用户名或密码错误、登录过于频繁、账号被锁定、参数不合法等。
 * 统一异常处理器会把它转换成 ApiResponse，而不是让它变成系统 500。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 业务响应码。
     */
    private final Integer code;

    public BusinessException(String message) {
        this(ResponseCode.BUSINESS_ERROR, message);
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
