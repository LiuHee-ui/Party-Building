package com.hongmai.exam.vo;

import lombok.Data;

import java.util.List;

/**
 * 用户端试卷。
 * 只含题目与选项文本，**不含标准答案**——答案留在服务端，判分也在服务端。
 */
@Data
public class ExamPaperVO {

    private Long paperId;
    private String title;
    private Integer totalScore;
    private Boolean submitted;

    /** 已提交时的得分，未提交为空 */
    private java.math.BigDecimal score;

    private List<QuestionItem> questions;

    /** 题目项。刻意不包含 correctOptions 字段。 */
    @Data
    public static class QuestionItem {
        private Long id;
        private Integer type;
        private String typeLabel;
        private Integer score;
        private String stem;
        private List<OptionItem> options;
    }

    @Data
    public static class OptionItem {
        private String key;
        private String text;
    }
}
