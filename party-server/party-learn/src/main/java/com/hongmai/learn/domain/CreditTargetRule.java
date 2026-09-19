package com.hongmai.learn.domain;

import com.hongmai.common.enums.PeriodType;

import java.math.BigDecimal;

/**
 * 默认目标学时（纯函数）。
 *
 * 政策硬指标（spec F19）：
 *   普通党员        五年 ≥160 学时
 *   书记及班子成员   五年 ≥280 学时、每年脱产 ≥40 学时
 *
 * 换算到自然年：280 ÷ 5 = 56，160 ÷ 5 = 32。
 * 这里的「每年」是均摊值，用于年度进度视图；五年期指标仍按 280/160 判定。
 *
 * 优先级：个人设置（t_credit_target）> 本条默认规则。
 * 也就是说个人设置只覆盖「目标值」，不改变身份判定。
 */
public final class CreditTargetRule {

    public static final BigDecimal LEADER_FIVE_YEAR = new BigDecimal("280");
    public static final BigDecimal LEADER_YEAR = new BigDecimal("56");
    public static final BigDecimal MEMBER_FIVE_YEAR = new BigDecimal("160");
    public static final BigDecimal MEMBER_YEAR = new BigDecimal("32");

    private CreditTargetRule() {
    }

    /** 按身份与周期取默认目标学时。leaderFlag 为 1 表示党组织书记或班子成员。 */
    public static BigDecimal defaultTarget(boolean leader, PeriodType periodType) {
        if (periodType == PeriodType.FIVE_YEAR_PLAN) {
            return leader ? LEADER_FIVE_YEAR : MEMBER_FIVE_YEAR;
        }
        return leader ? LEADER_YEAR : MEMBER_YEAR;
    }

    /** 计算达标率百分比，保留 2 位小数。目标为 0 时返回 100（视为已达标）。 */
    public static BigDecimal achievementPercent(BigDecimal achieved, BigDecimal target) {
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("100.00");
        }
        BigDecimal actual = achieved == null ? BigDecimal.ZERO : achieved;
        return actual.multiply(new BigDecimal("100"))
                .divide(target, 2, java.math.RoundingMode.HALF_UP);
    }

    /** 是否达标（达到或超过目标）。 */
    public static boolean isAchieved(BigDecimal achieved, BigDecimal target) {
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        return achieved != null && achieved.compareTo(target) >= 0;
    }
}
