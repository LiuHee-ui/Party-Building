package com.hongmai.exam.domain;

import java.util.List;
import java.util.Set;

/**
 * 试卷题目完整定义。
 *
 * 本期没有独立题库表，题目完整存在 t_exam_paper.question_ids_json 里，
 * 因此这个 record 同时承载「展示所需」（题干、选项）与「判分所需」（标准答案）两部分。
 *
 * 安全约束：**correctOptions 绝不能出现在面向客户端的返回体中。**
 * 对外必须先经 toJudgingQuestion() 之外的路径剥离答案，
 * 或者干脆只取 stem/options 组装 VO（ExamService 采用后者）。
 */
public record PaperQuestion(
        long id,
        int type,
        int score,
        String stem,
        List<Option> options,
        Set<String> correctOptions
) {

    public record Option(String key, String text) {
    }

    /** 转为判分用的轻量题目，只保留判分需要的字段。 */
    public ExamJudge.Question toJudgingQuestion() {
        return new ExamJudge.Question(id, type, score, correctOptions);
    }
}
