package com.campus.trade.common;

import lombok.Getter;

/**
 * 业务异常：由 GlobalExceptionHandler 转成统一返回体（401/403 同时设置 HTTP 状态码）。
 */
@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String msg) {
        super(msg);
        this.code = errorCode.getCode();
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
