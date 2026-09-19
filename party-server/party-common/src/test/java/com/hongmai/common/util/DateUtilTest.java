package com.hongmai.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 时区与统计周期测试（承接 plan §4.1「Asia/Shanghai 切日」）。
 * 切日边界直接决定「单条资源单日封顶 120 分钟」的归属，是最容易出错的点。
 */
class DateUtilTest {

    @Test
    @DisplayName("北京时间 23:50 归属当日")
    void beforeMidnightBelongsToCurrentDay() {
        // UTC 15:50 == 北京时间 23:50（同一天）
        Instant moment = Instant.parse("2026-09-17T15:50:00Z");
        assertEquals(LocalDate.of(2026, 9, 17), DateUtil.statDateOfInstant(moment));
    }

    @Test
    @DisplayName("跨过北京时间零点即归属次日（UTC 16:10）")
    void afterMidnightBelongsToNextDay() {
        // UTC 16:10 == 北京时间次日 00:10
        Instant moment = Instant.parse("2026-09-17T16:10:00Z");
        assertEquals(LocalDate.of(2026, 9, 18), DateUtil.statDateOfInstant(moment));
    }

    @Test
    @DisplayName("同一 UTC 日期的两个时刻可能落在不同的统计日")
    void sameUtcDateDifferentStatDate() {
        Instant before = Instant.parse("2026-09-17T15:59:59Z");
        Instant after = Instant.parse("2026-09-17T16:00:00Z");
        assertEquals(LocalDate.of(2026, 9, 17), DateUtil.statDateOfInstant(before));
        assertEquals(LocalDate.of(2026, 9, 18), DateUtil.statDateOfInstant(after));
    }

    @Test
    @DisplayName("LocalDateTime 直接取其日期")
    void statDateOfLocalDateTime() {
        assertEquals(LocalDate.of(2026, 9, 17),
                DateUtil.statDateOf(LocalDateTime.of(2026, 9, 17, 23, 50)));
    }

    @Test
    @DisplayName("自然年 key")
    void yearKey() {
        assertEquals("2026", DateUtil.yearKey(LocalDate.of(2026, 1, 1)));
    }

    @Test
    @DisplayName("五年规划期 key 按 2021 起算")
    void fiveYearPeriodKey() {
        assertEquals("2021-2025", DateUtil.fiveYearPeriodKey(LocalDate.of(2021, 1, 1)));
        assertEquals("2021-2025", DateUtil.fiveYearPeriodKey(LocalDate.of(2025, 12, 31)));
        assertEquals("2026-2030", DateUtil.fiveYearPeriodKey(LocalDate.of(2026, 1, 1)));
        assertEquals("2026-2030", DateUtil.fiveYearPeriodKey(LocalDate.of(2030, 12, 31)));
    }

    @Test
    @DisplayName("排行榜各周期 key 格式")
    void rankingPeriodKeys() {
        LocalDate date = LocalDate.of(2026, 9, 17);
        assertEquals("2026-W38", DateUtil.weekKey(date));
        assertEquals("2026-09", DateUtil.monthKey(date));
        assertEquals("2026-Q3", DateUtil.quarterKey(date));
        assertEquals("2026", DateUtil.yearKey(date));
    }

    @Test
    @DisplayName("按周期类型分发 key")
    void rankingPeriodKeyDispatch() {
        LocalDate date = LocalDate.of(2026, 9, 17);
        assertEquals("2026-W38", DateUtil.rankingPeriodKey(date, 1));
        assertEquals("2026-09", DateUtil.rankingPeriodKey(date, 2));
        assertEquals("2026-Q3", DateUtil.rankingPeriodKey(date, 3));
        assertEquals("2026", DateUtil.rankingPeriodKey(date, 4));
    }

    @Test
    @DisplayName("未知周期类型抛异常")
    void unknownPeriodTypeRejected() {
        try {
            DateUtil.rankingPeriodKey(LocalDate.of(2026, 9, 17), 9);
            throw new AssertionError("应当抛出异常");
        } catch (IllegalArgumentException expected) {
            // 符合预期
        }
    }
}
