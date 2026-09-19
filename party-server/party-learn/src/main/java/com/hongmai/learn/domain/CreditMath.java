package com.hongmai.learn.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 学时折算（纯函数）。
 *
 * 两条硬规则（spec F8）：
 *   1. 45 分钟 = 1 学时
 *   2. 单条资源单日最多计 2 学时（= 120 分钟）
 *
 * 第 2 条是防刷的关键闸门：没有它，用户挂着同一个视频循环播一天就能刷满整个年度的学时。
 *
 * 折算保留 2 位小数、HALF_UP。**不在这里做跨资源汇总的封顶**——
 * 单日总上限属于考核口径，不是记录口径，要放在统计层而不是入账层，
 * 否则「学习记录」会失真，无法回答「他今天实际学了多久」。
 */
public final class CreditMath {

    /** 多少分钟折算为 1 学时 */
    public static final int MINUTES_PER_CREDIT = 45;

    /** 单条资源单日可计入的分钟上限 */
    public static final int DAILY_CAP_MINUTES_PER_RESOURCE = 120;

    private CreditMath() {
    }

    /**
     * 本次可实际计入的分钟增量。
     * 当日该资源已计入 todayMinutes 时，最多再计入到封顶值。
     *
     * @return 可计入分钟数，已封顶，不会为负
     */
    public static int acceptableMinutes(int todayMinutes, int deltaMinutes) {
        if (deltaMinutes <= 0) {
            return 0;
        }
        int already = Math.max(0, todayMinutes);
        int remaining = DAILY_CAP_MINUTES_PER_RESOURCE - already;
        if (remaining <= 0) {
            return 0;
        }
        return Math.min(deltaMinutes, remaining);
    }

    /** 秒转分钟，向下取整——不足 1 分钟不计，避免零碎秒数累积成学时。 */
    public static int toMinutes(int seconds) {
        if (seconds <= 0) {
            return 0;
        }
        return seconds / 60;
    }

    /** 分钟折算学时，保留 2 位小数、HALF_UP。 */
    public static BigDecimal toCredit(int minutes) {
        if (minutes <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(MINUTES_PER_CREDIT), 2, RoundingMode.HALF_UP);
    }

    /** 该资源当日是否已触及封顶。 */
    public static boolean reachedDailyCap(int todayMinutes) {
        return todayMinutes >= DAILY_CAP_MINUTES_PER_RESOURCE;
    }
}
