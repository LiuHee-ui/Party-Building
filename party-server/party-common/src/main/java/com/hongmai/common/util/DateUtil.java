package com.hongmai.common.util;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * 时间与统计周期工具。
 * 全局统一 Asia/Shanghai（UTC+8），所有统计日按该时区切日——23:50 计当日、次日 00:10 计次日。
 */
public final class DateUtil {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private DateUtil() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static Instant nowInstant() {
        return Instant.now();
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** 某时刻在该时区下的统计日。 */
    public static LocalDate statDateOf(LocalDateTime moment) {
        return moment.atZone(ZONE).withZoneSameInstant(ZONE).toLocalDate();
    }

    /**
     * 由绝对时刻推该时区下的统计日。
     * 用于跨日切分：UTC 15:50 → 北京时间 23:50 计当日；UTC 16:10 → 次日 00:10 计次日。
     */
    public static LocalDate statDateOfInstant(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    /** 自然年 key，如 2026。 */
    public static String yearKey(LocalDate date) {
        return String.valueOf(date.getYear());
    }

    /** 五年规划期 key，如 2021-2025。 */
    public static String fiveYearPeriodKey(LocalDate date) {
        int start = ((date.getYear() - 2021) / 5) * 5 + 2021;
        return start + "-" + (start + 4);
    }

    /** 周 key，ISO 周，如 2026-W37。 */
    public static String weekKey(LocalDate date) {
        WeekFields wf = WeekFields.ISO;
        int week = date.get(wf.weekOfWeekBasedYear());
        return String.format("%d-W%02d", date.get(wf.weekBasedYear()), week);
    }

    /** 月 key，如 2026-09。 */
    public static String monthKey(LocalDate date) {
        return String.format("%d-%02d", date.getYear(), date.getMonthValue());
    }

    /** 季 key，如 2026-Q3。 */
    public static String quarterKey(LocalDate date) {
        int quarter = (date.getMonthValue() - 1) / 3 + 1;
        return date.getYear() + "-Q" + quarter;
    }

    /** 按排行榜周期类型取 key。 */
    public static String rankingPeriodKey(LocalDate date, int periodType) {
        return switch (periodType) {
            case 1 -> weekKey(date);
            case 2 -> monthKey(date);
            case 3 -> quarterKey(date);
            case 4 -> yearKey(date);
            default -> throw new IllegalArgumentException("未知的排行榜周期类型: " + periodType);
        };
    }

    public static LocalDate firstDayOfWeek(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }
}
