package com.hongmai.common.web;

import com.hongmai.common.exception.ErrorCode;
import lombok.Data;

/**
 * 统一响应体。HTTP 状态码固定 200，业务结果由 code 表达。
 * messageKey 供小程序端按当前语言渲染（承接 N9），message 为中文兜底文案便于排障。
 */
@Data
public class R<T> {

    private int code;
    private String messageKey;
    private String message;
    private T data;
    private String traceId;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = ErrorCode.SUCCESS.getCode();
        r.messageKey = ErrorCode.SUCCESS.getMessageKey();
        r.message = ErrorCode.SUCCESS.getMessage();
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessageKey(), errorCode.getMessage());
    }

    public static <T> R<T> fail(int code, String messageKey, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.messageKey = messageKey;
        r.message = message;
        return r;
    }

    public boolean isSuccess() {
        return code == ErrorCode.SUCCESS.getCode();
    }
}
