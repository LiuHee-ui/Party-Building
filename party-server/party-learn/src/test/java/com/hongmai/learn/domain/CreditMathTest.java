package com.hongmai.learn.domain;

import com.hongmai.common.enums.PeriodType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 学时折算与目标学时测试。
 * 对应 spec F8（45 分钟 = 1 学时、单条资源单日封顶 2 学时）与 F19（政策指标）。
 */
class CreditMathTest {

    // ---------- 折算 ----------

    @Test
    @DisplayName("45 分钟 = 1 学时")
    void fortyFiveMinutesIsOneCredit() {
        assertEquals(new BigDecimal("1.00"), CreditMath.toCredit(45));
        assertEquals(new BigDecimal("2.00"), CreditMath.toCredit(90));
    }

    @Test
    @DisplayName("不足 45 分钟按比例保留两位小数")
    void partialCreditRounded() {
        assertEquals(new BigDecimal("0.33"), CreditMath.toCredit(15));
        assertEquals(new BigDecimal("0.67"), CreditMath.toCredit(30));
        assertEquals(new BigDecimal("1.11"), CreditMath.toCredit(50));
    }

    @Test
    @DisplayName("非正数分钟折算为 0")
    void nonPositiveMinutesZeroCredit() {
        assertEquals(new BigDecimal("0.00"), CreditMath.toCredit(0));
        assertEquals(new BigDecimal("0.00"), CreditMath.toCredit(-10));
    }

    @Test
    @DisplayName("秒转分钟向下取整，零碎秒数不累积成学时")
    void secondsToMinutesFloor() {
        assertEquals(0, CreditMath.toMinutes(59));
        assertEquals(1, CreditMath.toMinutes(60));
        assertEquals(2, CreditMath.toMinutes(179));
        assertEquals(0, CreditMath.toMinutes(-30));
    }

    // ---------- 单日封顶 ----------

    @Test
    @DisplayName("未到封顶时可全额计入")
    void underCapAcceptsAll() {
        assertEquals(30, CreditMath.acceptableMinutes(0, 30));
        assertEquals(50, CreditMath.acceptableMinutes(70, 50));
    }

    @Test
    @DisplayName("触及封顶时按剩余额度部分计入")
    void partialAcceptNearCap() {
        // 当日已计 110 分钟，还剩 10 分钟额度
        assertEquals(10, CreditMath.acceptableMinutes(110, 30));
        assertEquals(10, CreditMath.acceptableMinutes(CreditMath.DAILY_CAP_MINUTES_PER_RESOURCE - 10, 999));
    }

    @Test
    @DisplayName("已达封顶时不再计入")
    void atCapAcceptsNothing() {
        assertEquals(0, CreditMath.acceptableMinutes(CreditMath.DAILY_CAP_MINUTES_PER_RESOURCE, 60));
        assertEquals(0, CreditMath.acceptableMinutes(200, 60));
    }

    @Test
    @DisplayName("恰好补满封顶额度")
    void exactlyFillsCap() {
        int todayMinutes = 60;
        int accepted = CreditMath.acceptableMinutes(todayMinutes, 60);
        assertEquals(60, accepted);
        assertEquals(CreditMath.DAILY_CAP_MINUTES_PER_RESOURCE, todayMinutes + accepted);
    }

    @Test
    @DisplayName("非正增量不计入")
    void nonPositiveDeltaAcceptsNothing() {
        assertEquals(0, CreditMath.acceptableMinutes(0, 0));
        assertEquals(0, CreditMath.acceptableMinutes(0, -5));
    }

    @Test
    @DisplayName("封顶判定")
    void capReachedCheck() {
        assertFalse(CreditMath.reachedDailyCap(119));
        assertTrue(CreditMath.reachedDailyCap(120));
        assertTrue(CreditMath.reachedDailyCap(121));
    }
}
