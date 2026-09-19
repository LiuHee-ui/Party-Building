package com.hongmai.common.storage;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * 直传凭证。
 * 本地实现只用到 url 与 objectKey；对象存储实现会额外填充 method 与 formFields。
 */
public record UploadTicket(
        String objectKey,
        String url,
        String method,
        Map<String, String> formFields,
        LocalDateTime expireAt
) {

    public static UploadTicket of(String objectKey, String url, LocalDateTime expireAt) {
        return new UploadTicket(objectKey, url, "POST", Collections.emptyMap(), expireAt);
    }
}
