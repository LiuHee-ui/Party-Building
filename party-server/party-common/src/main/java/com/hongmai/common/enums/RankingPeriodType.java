package com.hongmai.common.enums;

import lombok.Getter;

/**
 * 排行榜周期类型。
 *
 * **为什么必须独立于 PeriodType**：PeriodType 表达的是「学时统计口径」
 * （1 自然年 / 2 五年规划期），而这里表达的是「榜单换榜周期」（1 周 / 2 月 / 3 季 / 4 年）。
 * 两者恰好都用 1、2 打头，早期版本直接把 PeriodType.NATURAL_YEAR.getCode() 当成榜单周期传，
 * 结果学时被写进了「周榜」的分片，而查询读的是「年榜」——**Redis 里两个键都合法，
 * 不报任何错，只是榜单永远是空的**。
 *
 * 这类"两个枚举编码撞车"的问题不会有异常、不会有日志，只能靠类型和命名区分。
 * 所以单独定义这个枚举，并禁止在其他地方复用 PeriodType 的编码充当榜单周期。
 */
@Getter
public enum RankingPeriodType {

    WEEK(1, "本周"),
    MONTH(2, "本月"),
    QUARTER(3, "本季"),
    YEAR(4, "本年");

    private final int code;
    private final String label;

    RankingPeriodType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static RankingPeriodType of(int code) {
        for (RankingPeriodType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知排行榜周期类型: " + code);
    }

    public static boolean isValid(int code) {
        for (RankingPeriodType type : values()) {
            if (type.code == code) {
                return true;
            }
        }
        return false;
    }
}
