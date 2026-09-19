package com.hongmai.auth.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/** 身份证明图片列表与 JSON 列的互转。解析失败时返回空列表，不抛异常中断查询。 */
public final class ProofUrlsCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProofUrlsCodec() {
    }

    public static String encode(List<String> urls) {
        try {
            return MAPPER.writeValueAsString(urls == null ? List.of() : urls);
        } catch (Exception e) {
            throw new IllegalArgumentException("身份证明图片列表序列化失败", e);
        }
    }

    public static List<String> decode(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
