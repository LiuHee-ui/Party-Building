package com.hongmai.exam.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 试卷题目 JSON 编解码。
 *
 * 标准答案就存在这份 JSON 里，因此它**绝不能出现在任何面向客户端的返回体中**——
 * 给用户的题目必须经过 ExamJudge 的答案剥离。这是本模块最关键的一条安全约束。
 */
public final class PaperQuestionsCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PaperQuestionsCodec() {
    }

    public static String encode(List<PaperQuestion> questions) {
        try {
            return MAPPER.writeValueAsString(questions == null ? List.of() : questions);
        } catch (Exception e) {
            throw new IllegalArgumentException("试卷题目序列化失败", e);
        }
    }

    public static List<PaperQuestion> decode(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<PaperQuestion>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("试卷题目解析失败，数据可能已损坏", e);
        }
    }
}
