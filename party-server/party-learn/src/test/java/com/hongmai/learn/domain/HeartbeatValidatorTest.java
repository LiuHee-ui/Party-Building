package com.hongmai.learn.domain;

import com.hongmai.common.enums.HeartbeatRejectReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 心跳校验测试。
 * 每一条校验都对应一个真实的作弊路径，漏一条就能刷学时。
 */
class HeartbeatValidatorTest {

    private static final long NORMAL_INTERVAL = 15_000L;

    @Test
    @DisplayName("正常心跳被接受，计入声称的间隔秒数")
    void normalHeartbeatAccepted() {
        var decision = HeartbeatValidator.evaluate(5, 15, 4, NORMAL_INTERVAL);

        assertTrue(decision.accepted());
        assertEquals(15, decision.acceptedSec());
        assertNull(decision.reason());
    }

    @Test
    @DisplayName("首次心跳不受限流影响")
    void firstHeartbeatAccepted() {
        var decision = HeartbeatValidator.evaluate(1, 15, 0, Long.MAX_VALUE);
        assertTrue(decision.accepted());
        assertEquals(15, decision.acceptedSec());
    }

    // ---------- 幂等 ----------

    @Test
    @DisplayName("重复序号被判为重放，不重复计入（断网补传场景）")
    void duplicatedSeqIsReplay() {
        var decision = HeartbeatValidator.evaluate(3, 15, 3, NORMAL_INTERVAL);

        assertFalse(decision.accepted());
        assertEquals(0, decision.acceptedSec());
        assertEquals(HeartbeatRejectReason.REPLAY, decision.reason());
    }

    @Test
    @DisplayName("序号小于已接受最大值同样按重放处理（乱序包）")
    void outOfOrderSeqIsReplay() {
        var decision = HeartbeatValidator.evaluate(2, 15, 7, NORMAL_INTERVAL);

        assertFalse(decision.accepted());
        assertEquals(HeartbeatRejectReason.REPLAY, decision.reason());
    }

    @Test
    @DisplayName("序号恰好大于已接受最大值时放行")
    void seqJustAboveMaxAccepted() {
        assertTrue(HeartbeatValidator.evaluate(8, 15, 7, NORMAL_INTERVAL).accepted());
    }

    // ---------- 限流 ----------

    @Test
    @DisplayName("间隔过短被判限流，且不给截断机会")
    void tooFrequentIsRateLimited() {
        var decision = HeartbeatValidator.evaluate(9, 15, 8, 500L);

        assertFalse(decision.accepted());
        assertEquals(0, decision.acceptedSec(), "限流时不得计入任何秒数，否则脚本可高频积少成多");
        assertEquals(HeartbeatRejectReason.RATE_LIMITED, decision.reason());
    }

    @Test
    @DisplayName("恰好达到最小间隔时放行")
    void exactlyMinIntervalAccepted() {
        assertTrue(HeartbeatValidator.evaluate(9, 15, 8,
                HeartbeatValidator.MIN_ACCEPT_INTERVAL_MILLIS).accepted());
    }

    // ---------- 增量截断 ----------

    @Test
    @DisplayName("间隔超过上限时按上限截断而不是整条丢弃（弱网用户需要拿到学时）")
    void overDeltaIsTruncatedNotDropped() {
        var decision = HeartbeatValidator.evaluate(10, 3600, 9, 3_600_000L);

        assertTrue(decision.accepted(), "锁屏一小时后恢复，应计上限时长而不是直接丢弃");
        assertEquals(HeartbeatValidator.MAX_DELTA_SEC, decision.acceptedSec());
        assertEquals(HeartbeatRejectReason.OVER_DELTA, decision.reason());
    }

    @Test
    @DisplayName("恰好等于上限时不截断")
    void exactlyMaxDeltaNotTruncated() {
        var decision = HeartbeatValidator.evaluate(10, HeartbeatValidator.MAX_DELTA_SEC, 9, 60_000L);

        assertTrue(decision.accepted());
        assertEquals(HeartbeatValidator.MAX_DELTA_SEC, decision.acceptedSec());
        assertNull(decision.reason());
    }

    @Test
    @DisplayName("间隔非正数被判非法，防止 0 秒心跳刷序号")
    void nonPositiveDeltaRejected() {
        assertEquals(HeartbeatRejectReason.OUT_OF_ORDER,
                HeartbeatValidator.evaluate(11, 0, 10, NORMAL_INTERVAL).reason());
        assertEquals(HeartbeatRejectReason.OUT_OF_ORDER,
                HeartbeatValidator.evaluate(11, -5, 10, NORMAL_INTERVAL).reason());
    }

    @Test
    @DisplayName("校验顺序：重复序号优先于限流判定")
    void replayCheckedBeforeRateLimit() {
        // 序号重复且间隔极短，应报重放而不是限流——重放不需要写限流日志
        var decision = HeartbeatValidator.evaluate(3, 15, 3, 100L);
        assertEquals(HeartbeatRejectReason.REPLAY, decision.reason());
    }

    @Test
    @DisplayName("一次心跳最多计入 60 秒，堵住「挂机一小时」")
    void singleHeartbeatContributionBounded() {
        for (int delta : new int[]{61, 120, 600, 3600}) {
            var decision = HeartbeatValidator.evaluate(100, delta, 99, Long.MAX_VALUE);
            assertTrue(decision.acceptedSec() <= HeartbeatValidator.MAX_DELTA_SEC,
                    "delta=" + delta + " 时计入秒数超过上限");
        }
    }
}
