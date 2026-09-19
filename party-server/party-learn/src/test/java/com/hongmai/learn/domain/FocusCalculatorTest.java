package com.hongmai.learn.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 失焦口径测试。
 * 核心是「累计失焦超过 600 秒后才扣超出部分」——这条口径直接决定用户会不会被误扣学时。
 */
class FocusCalculatorTest {

    private static final int FORGIVE = FocusCalculator.UNFOCUS_FORGIVE_SEC;

    @Test
    @DisplayName("全程聚焦时失焦为 0")
    void fullyFocusedHasNoUnfocus() {
        assertEquals(0, FocusCalculator.unfocusedSecOf(15, 15));
    }

    @Test
    @DisplayName("焦点秒数超过间隔时按间隔计，失焦不为负")
    void focusedBeyondDeltaClamped() {
        assertEquals(0, FocusCalculator.unfocusedSecOf(15, 100));
    }

    @Test
    @DisplayName("焦点秒数为负时整段算失焦")
    void negativeFocusedTreatedAsZero() {
        assertEquals(15, FocusCalculator.unfocusedSecOf(15, -3));
    }

    @Test
    @DisplayName("间隔非正数时不计失焦")
    void nonPositiveDeltaNoUnfocus() {
        assertEquals(0, FocusCalculator.unfocusedSecOf(0, 0));
        assertEquals(0, FocusCalculator.unfocusedSecOf(-5, 0));
    }

    // ---------- 豁免额度 ----------

    @Test
    @DisplayName("失焦在豁免额度内时不扣有效时长")
    void unfocusWithinForgiveIsFree() {
        assertEquals(900, FocusCalculator.effectiveSec(900, 0));
        assertEquals(900, FocusCalculator.effectiveSec(900, 300));
        assertEquals(900, FocusCalculator.effectiveSec(900, FORGIVE));
    }

    @Test
    @DisplayName("失焦超出豁免额度的部分才从有效时长中扣除")
    void onlyExcessUnfocusDeducted() {
        // 聚焦 900 秒，失焦 700 秒 → 只扣超出的 100 秒，剩 800
        assertEquals(800, FocusCalculator.effectiveSec(900, 700));
        // 失焦恰好等于额度 → 一分不扣
        assertEquals(900, FocusCalculator.effectiveSec(900, FORGIVE));
    }

    @Test
    @DisplayName("有效时长不会为负")
    void effectiveNeverNegative() {
        assertEquals(0, FocusCalculator.effectiveSec(60, 100_000));
        assertEquals(0, FocusCalculator.effectiveSec(0, FORGIVE + 500));
    }

    @Test
    @DisplayName("多次短失焦累计后仍会被扣——逐次判断抓不住这种情况")
    void manyShortUnfocusAccumulate() {
        // 10 次各失焦 90 秒，合计 900 秒，单次看都不长
        int unfocused = 0;
        for (int i = 0; i < 10; i++) {
            unfocused = FocusCalculator.accumulateUnfocused(unfocused, 90);
        }
        assertEquals(900, unfocused);
        // 累计 900 秒，扣掉超出 600 的 300 秒
        assertEquals(600, FocusCalculator.effectiveSec(900, unfocused));
    }

    // ---------- 增量口径 ----------

    @Test
    @DisplayName("豁免额度未用尽时，本次聚焦全部计入")
    void deltaBeforeExhaustingForgiveCountedFully() {
        assertEquals(15, FocusCalculator.effectiveDeltaSec(15, 15, 0));
        assertEquals(10, FocusCalculator.effectiveDeltaSec(10, 15, 200));
    }

    @Test
    @DisplayName("本次失焦跨过豁免额度时，只扣超出的那部分")
    void deltaCrossingForgiveBoundary() {
        // 累计失焦 595，本次失焦 10 → 超 5 秒，本次聚焦 5 秒
        int effective = FocusCalculator.effectiveDeltaSec(5, 15, 595);
        assertEquals(0, effective, "本次聚焦 5 秒，超出豁免 5 秒，正好抵消");

        // 累计失焦 600（额度刚好用尽），本次全聚焦 → 全额计入
        assertEquals(15, FocusCalculator.effectiveDeltaSec(15, 15, FORGIVE));
    }

    @Test
    @DisplayName("豁免额度已用尽后，每次失焦都全额扣除")
    void afterForgiveExhaustedAllUnfocusDeducted() {
        // 累计失焦 700（已超 100），本次聚焦 5、间隔 15 → 失焦 10，有效 0
        assertEquals(0, FocusCalculator.effectiveDeltaSec(5, 15, 700));
        // 本次全聚焦则全额计入
        assertEquals(15, FocusCalculator.effectiveDeltaSec(15, 15, 700));
    }

    @Test
    @DisplayName("增量结果永不为负")
    void deltaNeverNegative() {
        assertEquals(0, FocusCalculator.effectiveDeltaSec(0, 15, 0));
        assertEquals(0, FocusCalculator.effectiveDeltaSec(-1, 15, 5000));
        // 豁免额度早已用尽、但本次全程聚焦 → 仍按聚焦时长全额计入
        assertEquals(3, FocusCalculator.effectiveDeltaSec(3, 3, 5000));
        assertTrue(FocusCalculator.effectiveDeltaSec(13, 15, 9999) >= 0);
    }
}
