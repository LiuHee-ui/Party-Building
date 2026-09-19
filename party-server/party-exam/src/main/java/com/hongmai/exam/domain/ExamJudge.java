package com.hongmai.exam.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 答题判分（纯函数）。
 *
 * 核心原则：**判分只在服务端做**。客户端提交的是「选了哪些选项」，
 * 不是「得了多少分」——否则改一个数字就能拿满分。
 *
 * 判分规则：选项集合必须与正确答案**完全一致**才算对。
 * 多选题少选、多选一律按错处理，不给部分分。
 * 之所以不给部分分：这是党建知识考核，口径要简明可解释——
 * 「多选少选都算错」比一套复杂的加权规则更容易向党员解释清楚，也更难被钻空子。
 */
public final class ExamJudge {

    /** 题型：1 单选 / 2 多选 / 3 判断 */
    public static final int TYPE_SINGLE = 1;
    public static final int TYPE_MULTI = 2;
    public static final int TYPE_TRUE_FALSE = 3;

    private ExamJudge() {
    }

    /** 题目定义。correctOptions 是标准答案的选项 key 集合。 */
    public record Question(long id, int type, int score, Set<String> correctOptions) {
    }

    /** 单题判定明细，落库到 t_exam_record.detail_json。 */
    public record QuestionDetail(long questionId, int type, int score, boolean correct,
                                 List<String> selected, List<String> correctOptions) {
    }

    /** 判分结果。 */
    public record JudgeResult(int correctCount, int totalCount, int totalScore,
                              BigDecimal score, List<QuestionDetail> details) {
    }

    /**
     * 判分。
     *
     * @param questions 试卷题目（含答案，只存在于服务端）
     * @param answers   用户提交：题目 id → 选中选项
     */
    public static JudgeResult judge(List<Question> questions, Map<Long, List<String>> answers) {
        List<QuestionDetail> details = new ArrayList<>(questions.size());
        int correctCount = 0;
        int totalScore = 0;
        int gainedScore = 0;

        for (Question question : questions) {
            totalScore += question.score();
            List<String> selected = normalize(answers == null ? null : answers.get(question.id()));
            boolean correct = matches(selected, question.correctOptions());
            if (correct) {
                correctCount++;
                gainedScore += question.score();
            }
            details.add(new QuestionDetail(question.id(), question.type(), question.score(), correct,
                    selected, sorted(question.correctOptions())));
        }

        BigDecimal score = totalScore <= 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(gainedScore)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalScore), 2, RoundingMode.HALF_UP);

        return new JudgeResult(correctCount, questions.size(), totalScore, score, details);
    }

    /** 是否答对：集合完全一致。 */
    private static boolean matches(List<String> selected, Set<String> correct) {
        if (selected == null || selected.isEmpty()) {
            return false;
        }
        if (correct == null || correct.isEmpty()) {
            return false;
        }
        return new HashSet<>(selected).equals(new HashSet<>(correct));
    }

    /** 去重、去空白、去 null，并保证顺序稳定（便于比对与落库）。 */
    private static List<String> normalize(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    private static List<String> sorted(Set<String> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        return options.stream().filter(v -> v != null && !v.isBlank()).sorted().toList();
    }

    /** 是否及格（按百分制 60 分）。 */
    public static boolean isPassed(BigDecimal score) {
        return score != null && score.compareTo(BigDecimal.valueOf(60)) >= 0;
    }
}
