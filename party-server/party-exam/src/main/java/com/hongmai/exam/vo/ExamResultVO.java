package com.hongmai.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 交卷结果。判分在服务端完成，客户端只负责展示。 */
@Data
public class ExamResultVO {

    private Long recordId;
    private Long paperId;
    private BigDecimal score;
    private Integer correctCount;
    private Integer totalCount;
    private Boolean passed;

    /** 本组织「答题得分」榜中的名次，无榜时为空 */
    private Long rankInOrg;

    private List<QuestionResult> details;

    @Data
    public static class QuestionResult {
        private Long questionId;
        private Integer type;
        private Integer score;
        private Boolean correct;
        private List<String> selected;
        /** 交卷后才回显标准答案，供错题回顾 */
        private List<String> correctOptions;
    }
}
