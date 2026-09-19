package com.hongmai.exam.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 榜单周期区间测试。
 * 区间与 periodKey 必须由同一处生成——两者不一致会让快照统计的范围和榜单的标识对不上，
 * 表现为「榜单里有数据但明细查不到」。
 */
class PeriodRangeTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 17);

    @Test
    @DisplayName("周：周一到周日")
    void weekRange() {
        PeriodRange range = PeriodRange.current(PeriodRange.WEEK, DATE);

        assertEquals(LocalDate.of(2026, 9, 14), range.from());
        assertEquals(LocalDate.of(2026, 9, 20), range.to());
        assertEquals("2026-W38", range.periodKey());
    }

    @Test
    @DisplayName("月：1 号到月末")
    void monthRange() {
        PeriodRange range = PeriodRange.current(PeriodRange.MONTH, DATE);

        assertEquals(LocalDate.of(2026, 9, 1), range.from());
        assertEquals(LocalDate.of(2026, 9, 30), range.to());
        assertEquals("2026-09", range.periodKey());
    }

    @Test
    @DisplayName("季：季度首日到末日")
    void quarterRange() {
        PeriodRange q3 = PeriodRange.current(PeriodRange.QUARTER, DATE);
        assertEquals(LocalDate.of(2026, 7, 1), q3.from());
        assertEquals(LocalDate.of(2026, 9, 30), q3.to());
        assertEquals("2026-Q3", q3.periodKey());

        PeriodRange q1 = PeriodRange.current(PeriodRange.QUARTER, LocalDate.of(2026, 1, 1));
        assertEquals(LocalDate.of(2026, 1, 1), q1.from());
        assertEquals(LocalDate.of(2026, 3, 31), q1.to());

        PeriodRange q4 = PeriodRange.current(PeriodRange.QUARTER, LocalDate.of(2026, 12, 31));
        assertEquals(LocalDate.of(2026, 10, 1), q4.from());
        assertEquals(LocalDate.of(2026, 12, 31), q4.to());
    }

    @Test
    @DisplayName("年：1 月 1 日到 12 月 31 日")
    void yearRange() {
        PeriodRange range = PeriodRange.current(PeriodRange.YEAR, DATE);

        assertEquals(LocalDate.of(2026, 1, 1), range.from());
        assertEquals(LocalDate.of(2026, 12, 31), range.to());
        assertEquals("2026", range.periodKey());
    }

    @Test
    @DisplayName("周日属于上一周还是新一周：按 ISO 归入所在周（周日起算的下一周）")
    void sundayBelongsToItsWeek() {
        // 2026-09-20 是周日，属于 2026-W38
        PeriodRange range = PeriodRange.current(PeriodRange.WEEK, LocalDate.of(2026, 9, 20));
        assertEquals(LocalDate.of(2026, 9, 14), range.from());
        assertEquals(LocalDate.of(2026, 9, 20), range.to());
        assertEquals("2026-W38", range.periodKey());
    }

    @Test
    @DisplayName("跨月周：周一在上月，区间仍以自然周为准")
    void crossMonthWeek() {
        // 2026-09-01 是周二，所在周从 2026-08-31（周一）开始
        PeriodRange range = PeriodRange.current(PeriodRange.WEEK, LocalDate.of(2026, 9, 1));
        assertEquals(LocalDate.of(2026, 8, 31), range.from());
        assertEquals(LocalDate.of(2026, 9, 6), range.to());
    }

    @Test
    @DisplayName("二月区间正确处理闰年")
    void februaryLeapYear() {
        PeriodRange range = PeriodRange.current(PeriodRange.MONTH, LocalDate.of(2028, 2, 10));
        assertEquals(LocalDate.of(2028, 2, 1), range.from());
        assertEquals(LocalDate.of(2028, 2, 29), range.to());
    }

    @Test
    @DisplayName("allOf 一次返回四级周期，且区间都覆盖当天")
    void allOfCoversDate() {
        List<PeriodRange> ranges = PeriodRange.allOf(DATE);

        assertEquals(4, ranges.size());
        for (PeriodRange range : ranges) {
            assertEquals(true,
                    !DATE.isBefore(range.from()) && !DATE.isAfter(range.to()),
                    "周期 " + range.periodKey() + " 的区间未包含 " + DATE);
        }
    }

    @Test
    @DisplayName("未知周期类型抛异常")
    void unknownTypeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PeriodRange.current(99, DATE));
    }
}
