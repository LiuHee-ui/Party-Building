package com.hongmai.exam.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判分测试。
 * 判分是考试模块的核心，规则错一条成绩就不可信，所以这里覆盖得比较细。
 * 口径：选项集合必须与标准答案**完全一致**才算对，多选少选一律算错、不给部分分。
 */
class ExamJudgeTest {

    private static ExamJudge.Question single(long id, int score, String correct) {
        return new ExamJudge.Question(id, ExamJudge.TYPE_SINGLE, score, Set.of(correct));
    }

    private static ExamJudge.Question multi(long id, int score, String... correct) {
        return new ExamJudge.Question(id, ExamJudge.TYPE_MULTI, score, Set.of(correct));
    }

    private static Map<Long, List<String>> answers(Object... pairs) {
        Map<Long, List<String>> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Long) pairs[i], (List<String>) pairs[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("全对得满分")
    void allCorrectScoresFull() {
        List<ExamJudge.Question> questions = List.of(
                single(1, 40, "A"), single(2, 60, "B"));

        var result = ExamJudge.judge(questions, answers(
                1L, List.of("A"), 2L, List.of("B")));

        assertEquals(2, result.correctCount());
        assertEquals(2, result.totalCount());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.score()));
        assertTrue(ExamJudge.isPassed(result.score()));
    }

    @Test
    @DisplayName("全错得零分")
    void allWrongScoresZero() {
        var result = ExamJudge.judge(List.of(single(1, 100, "A")),
                answers(1L, List.of("D")));

        assertEquals(0, result.correctCount());
        assertEquals(0, new BigDecimal("0.00").compareTo(result.score()));
        assertFalse(ExamJudge.isPassed(result.score()));
    }

    @Test
    @DisplayName("部分对按分值加权，不是按题目数平均")
    void partialCorrectWeightedByScore() {
        // 第 1 题 30 分答对，第 2 题 70 分答错 → 30 分
        var result = ExamJudge.judge(List.of(single(1, 30, "A"), single(2, 70, "B")),
                answers(1L, List.of("A"), 2L, List.of("C")));

        assertEquals(1, result.correctCount());
        assertEquals(0, new BigDecimal("30.00").compareTo(result.score()));
    }

    @Test
    @DisplayName("未作答按错处理，不报异常")
    void unansweredTreatedAsWrong() {
        var result = ExamJudge.judge(List.of(single(1, 50, "A"), single(2, 50, "B")),
                answers(1L, List.of("A")));

        assertEquals(1, result.correctCount());
        assertEquals(0, new BigDecimal("50.00").compareTo(result.score()));
    }

    @Test
    @DisplayName("答案集合为 null 时全部按错处理")
    void nullAnswersAllWrong() {
        var result = ExamJudge.judge(List.of(single(1, 100, "A")), null);

        assertEquals(0, result.correctCount());
        assertEquals(0, new BigDecimal("0.00").compareTo(result.score()));
    }

    // ---------- 多选题口径 ----------

    @Test
    @DisplayName("多选题选全且无多余才正确")
    void multiSelectExactMatch() {
        var result = ExamJudge.judge(List.of(multi(1, 100, "A", "C")),
                answers(1L, List.of("C", "A")));

        assertTrue(result.details().get(0).correct(), "顺序不同不影响判定");
        assertEquals(0, new BigDecimal("100.00").compareTo(result.score()));
    }

    @Test
    @DisplayName("多选题少选算错，不给部分分")
    void multiSelectMissingIsWrong() {
        var result = ExamJudge.judge(List.of(multi(1, 100, "A", "C")),
                answers(1L, List.of("A")));

        assertFalse(result.details().get(0).correct());
        assertEquals(0, new BigDecimal("0.00").compareTo(result.score()));
    }

    @Test
    @DisplayName("多选题多选算错")
    void multiSelectExtraIsWrong() {
        var result = ExamJudge.judge(List.of(multi(1, 100, "A", "C")),
                answers(1L, List.of("A", "B", "C")));

        assertFalse(result.details().get(0).correct());
    }

    @Test
    @DisplayName("重复选项去重后不影响判定")
    void duplicatedSelectionDeduplicated() {
        var result = ExamJudge.judge(List.of(multi(1, 100, "A", "C")),
                answers(1L, List.of("A", "A", "C")));

        assertTrue(result.details().get(0).correct());
    }

    @Test
    @DisplayName("空白与 null 选项被过滤")
    void blankOptionsFiltered() {
        var result = ExamJudge.judge(List.of(single(1, 100, "A")),
                answers(1L, java.util.Arrays.asList(" A ", null, "  ")));

        assertTrue(result.details().get(0).correct());
    }

    // ---------- 明细 ----------

    @Test
    @DisplayName("明细回传选中项与标准答案，供错题回顾")
    void detailsCarryBothSides() {
        var result = ExamJudge.judge(List.of(multi(1, 100, "B", "A")),
                answers(1L, List.of("C")));

        var detail = result.details().get(0);
        assertEquals(List.of("C"), detail.selected());
        assertEquals(List.of("A", "B"), detail.correctOptions(), "标准答案按字典序输出，便于对比");
        assertFalse(detail.correct());
    }

    @Test
    @DisplayName("题目顺序与总分：总分为各题分值之和")
    void totalScoreIsSumOfQuestions() {
        var result = ExamJudge.judge(List.of(single(1, 20, "A"), single(2, 30, "B"), single(3, 50, "C")),
                answers(1L, List.of("A"), 2L, List.of("B"), 3L, List.of("C")));

        assertEquals(3, result.totalCount());
        assertEquals(100, result.totalScore());
    }

    @Test
    @DisplayName("行为异常时为空试卷安全兜底")
    void emptyPaperIsSafe() {
        var result = ExamJudge.judge(List.of(), Map.of());

        assertEquals(0, result.totalCount());
        assertEquals(0, new BigDecimal("0.00").compareTo(result.score()));
    }

    @Test
    @DisplayName("及格线为 60 分，含等于")
    void passLineIsInclusive() {
        assertTrue(ExamJudge.isPassed(new BigDecimal("60.00")));
        assertFalse(ExamJudge.isPassed(new BigDecimal("59.99")));
        assertFalse(ExamJudge.isPassed(null));
    }
}
