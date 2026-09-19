package com.hongmai.common.exception;

import lombok.Getter;

/** 业务异常。携带错误码，由 GlobalExceptionHandler 转为统一响应体。 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final String messageKey;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.messageKey = errorCode.getMessageKey();
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
        this.messageKey = errorCode.getMessageKey();
    }

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }
}
