package com.hongmai.learn.domain;

/**
 * 失焦时长口径（纯函数）。
 *
 * 业务背景：移动端用户切到后台、接电话、看消息，都会造成失焦。
 * 如果只要失焦就扣，正常用户的学时会被大量误扣；
 * 如果完全不扣，挂机刷时长就无法阻挡。
 *
 * 口径（spec F8）：**累计失焦超过 600 秒后，只扣超出部分**。
 * 也就是给每个学习时段 600 秒的「失焦豁免额度」，超出部分才从有效时长里扣。
 *
 * 为什么是「累计后扣超出」而不是「逐次判断」：
 *   用户可能 10 次各失焦 90 秒（合计 900 秒），每次单独看都「不长」，
 *   但累计已经远超阈值。累计口径才能抓住这种情况。
 */
public final class FocusCalculator {

    /** 单时段失焦豁免秒数 */
    public static final int UNFOCUS_FORGIVE_SEC = 600;

    private FocusCalculator() {
    }

    /** 本次心跳中属于失焦的秒数。焦点秒数不应超过间隔（客户端 bug 时按 0 处理）。 */
    public static int unfocusedSecOf(int deltaSec, int focusedSec) {
        if (deltaSec <= 0) {
            return 0;
        }
        int focused = Math.max(0, Math.min(focusedSec, deltaSec));
        return deltaSec - focused;
    }

    /** 累加失焦秒数。 */
    public static int accumulateUnfocused(int currentUnfocusedSec, int unfocusedThisRound) {
        if (unfocusedThisRound <= 0) {
            return Math.max(0, currentUnfocusedSec);
        }
        return Math.max(0, currentUnfocusedSec) + unfocusedThisRound;
    }

    /**
     * 计算截至当前的有效学习秒数。
     *
     * 有效 = 累计聚焦 − 可扣除的失焦（累计失焦超出豁免额度的部分）
     * 结果不会为负。
     */
    public static int effectiveSec(int totalFocusedSec, int totalUnfocusedSec) {
        int focused = Math.max(0, totalFocusedSec);
        int unfocused = Math.max(0, totalUnfocusedSec);
        int deductible = Math.max(0, unfocused - UNFOCUS_FORGIVE_SEC);
        return Math.max(0, focused - deductible);
    }

    /** 本次心跳增量对应的有效秒数增量（用于累加到会话与日累计）。 */
    public static int effectiveDeltaSec(int focusedSec, int deltaSec, int accumulatedUnfocusedSec) {
        int unfocusedNow = unfocusedSecOf(deltaSec, focusedSec);
        int unfocusedAfter = accumulateUnfocused(accumulatedUnfocusedSec, unfocusedNow);

        int deductibleBefore = Math.max(0, Math.max(0, accumulatedUnfocusedSec) - UNFOCUS_FORGIVE_SEC);
        int deductibleAfter = Math.max(0, unfocusedAfter - UNFOCUS_FORGIVE_SEC);

        // 本次新产生的「可扣失焦」按本次的聚焦时长抵扣，不会扣成负数
        int newlyDeductible = deductibleAfter - deductibleBefore;
        int focused = Math.max(0, Math.min(focusedSec, Math.max(0, deltaSec)));
        return Math.max(0, focused - newlyDeductible);
    }
}
