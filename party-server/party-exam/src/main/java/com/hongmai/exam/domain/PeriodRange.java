package com.hongmai.exam.domain;

import com.hongmai.common.util.DateUtil;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * 排行榜周期对应的日期区间（纯函数）。
 *
 * 快照任务需要按周期区间统计数据库里的原始数据，而 Redis 键里只有 periodKey（如 2026-09）。
 * 由 periodKey 反推区间既麻烦又容易出错，不如统一从「今天」正向算出来——
 * 区间与 key 由同一处生成，天然一致。
 */
public record PeriodRange(int periodType, String periodKey, LocalDate from, LocalDate to) {

    /**
     * 周期类型编码统一取自 RankingPeriodType。
     * 不要再在这里写 1/2/3/4 的字面量——它们与 PeriodType（1 自然年 / 2 五年期）编码相同但含义不同。
     */
    public static final int WEEK = com.hongmai.common.enums.RankingPeriodType.WEEK.getCode();
    public static final int MONTH = com.hongmai.common.enums.RankingPeriodType.MONTH.getCode();
    public static final int QUARTER = com.hongmai.common.enums.RankingPeriodType.QUARTER.getCode();
    public static final int YEAR = com.hongmai.common.enums.RankingPeriodType.YEAR.getCode();

    /** 取某天所在周期。 */
    public static PeriodRange current(int periodType, LocalDate date) {
        // 先解析成枚举再分支：switch 的 case 标签必须是编译期常量，
        // 而 WEEK/MONTH 这些常量现在是方法调用结果，只能用枚举来分支。
        // 顺带把非法编码在入口就挡掉。
        com.hongmai.common.enums.RankingPeriodType type =
                com.hongmai.common.enums.RankingPeriodType.of(periodType);

        LocalDate from;
        LocalDate to;
        switch (type) {
            case WEEK -> {
                from = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                to = from.plusDays(6);
            }
            case MONTH -> {
                from = date.withDayOfMonth(1);
                to = date.with(TemporalAdjusters.lastDayOfMonth());
            }
            case QUARTER -> {
                int startMonth = (date.getMonthValue() - 1) / 3 * 3 + 1;
                from = LocalDate.of(date.getYear(), startMonth, 1);
                to = from.plusMonths(3).minusDays(1);
            }
            case YEAR -> {
                from = LocalDate.of(date.getYear(), 1, 1);
                to = LocalDate.of(date.getYear(), 12, 31);
            }
            default -> throw new IllegalArgumentException("未知周期类型: " + periodType);
        }
        return new PeriodRange(periodType, DateUtil.rankingPeriodKey(date, periodType), from, to);
    }

    /** 取某天所在的全部四级周期，供快照任务一次算完。 */
    public static List<PeriodRange> allOf(LocalDate date) {
        List<PeriodRange> ranges = new ArrayList<>(4);
        for (int type : new int[]{WEEK, MONTH, QUARTER, YEAR}) {
            ranges.add(current(type, date));
        }
        return ranges;
    }
}
