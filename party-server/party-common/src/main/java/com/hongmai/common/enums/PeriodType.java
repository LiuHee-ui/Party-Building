package com.hongmai.common.enums;

import lombok.Getter;

/** 学时统计周期口径。默认展示自然年。 */
@Getter
public enum PeriodType {

    NATURAL_YEAR(1, "自然年"),
    FIVE_YEAR_PLAN(2, "五年规划期");

    private final int code;
    private final String label;

    PeriodType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static PeriodType of(int code) {
        for (PeriodType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知统计周期: " + code);
    }
}
