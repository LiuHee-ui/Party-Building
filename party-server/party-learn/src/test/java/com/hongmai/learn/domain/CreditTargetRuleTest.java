package com.hongmai.learn.domain;

import com.hongmai.common.enums.PeriodType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目标学时与达标判定测试。
 * 对应政策硬指标：普通党员五年 ≥160 学时；书记及班子成员五年 ≥280 学时。
 */
class CreditTargetRuleTest {

    @Test
    @DisplayName("普通党员：五年 160、年均 32")
    void memberTargets() {
        assertEquals(new BigDecimal("160"), CreditTargetRule.defaultTarget(false, PeriodType.FIVE_YEAR_PLAN));
        assertEquals(new BigDecimal("32"), CreditTargetRule.defaultTarget(false, PeriodType.NATURAL_YEAR));
    }

    @Test
    @DisplayName("书记及班子成员：五年 280、年均 56")
    void leaderTargets() {
        assertEquals(new BigDecimal("280"), CreditTargetRule.defaultTarget(true, PeriodType.FIVE_YEAR_PLAN));
        assertEquals(new BigDecimal("56"), CreditTargetRule.defaultTarget(true, PeriodType.NATURAL_YEAR));
    }

    @Test
    @DisplayName("年均目标 × 5 等于五年目标——换算必须自洽，否则两个视图互相矛盾")
    void yearTargetTimesFiveEqualsFiveYearTarget() {
        for (boolean leader : new boolean[]{true, false}) {
            BigDecimal year = CreditTargetRule.defaultTarget(leader, PeriodType.NATURAL_YEAR);
            BigDecimal fiveYear = CreditTargetRule.defaultTarget(leader, PeriodType.FIVE_YEAR_PLAN);
            assertEquals(0, year.multiply(new BigDecimal("5")).compareTo(fiveYear),
                    "leader=" + leader + " 时年均与五年目标不匹配");
        }
    }

    @Test
    @DisplayName("达标率保留两位小数")
    void achievementPercent() {
        assertEquals(new BigDecimal("50.00"),
                CreditTargetRule.achievementPercent(new BigDecimal("80"), new BigDecimal("160")));
        assertEquals(new BigDecimal("125.00"),
                CreditTargetRule.achievementPercent(new BigDecimal("35"), new BigDecimal("28")));
    }

    @Test
    @DisplayName("未产生学时但目标为 0 时视为已达标（避免除零）")
    void zeroTargetIsAlwaysAchieved() {
        assertEquals(new BigDecimal("100.00"),
                CreditTargetRule.achievementPercent(null, BigDecimal.ZERO));
        assertTrue(CreditTargetRule.isAchieved(null, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("达标判定按「达到或超过」")
    void achievedComparison() {
        assertTrue(CreditTargetRule.isAchieved(new BigDecimal("160"), new BigDecimal("160")));
        assertTrue(CreditTargetRule.isAchieved(new BigDecimal("200"), new BigDecimal("160")));
        assertFalse(CreditTargetRule.isAchieved(new BigDecimal("159.99"), new BigDecimal("160")));
        assertFalse(CreditTargetRule.isAchieved(null, new BigDecimal("160")));
    }
}
