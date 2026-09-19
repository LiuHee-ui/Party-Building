package com.hongmai.common.enums;

import lombok.Getter;

/** 排行榜分值来源。分值增量由服务端计算，客户端不可上报。 */
@Getter
public enum RankingMetric {

    LEARN_CREDIT("学时", "learn"),
    EXAM_SCORE("答题得分", "exam");

    private final String label;
    private final String keyPrefix;

    RankingMetric(String label, String keyPrefix) {
        this.label = label;
        this.keyPrefix = keyPrefix;
    }
}
