package com.hongmai.learn.domain;

import com.hongmai.common.enums.HeartbeatRejectReason;

/**
 * 心跳校验（纯函数，独立可测）。
 *
 * 客户端每 15 秒上报一次。这些校验是防刷的核心，每一条都对应一个真实的作弊路径：
 *   重复上报  —— 断网补传时重发旧心跳，若不去重会重复计学时
 *   序号回退  —— 客户端伪造大量序号刷时长
 *   高频上报  —— 用脚本以毫秒级间隔狂发心跳
 *   超长间隔  —— 上报间隔 1 小时但声称一直在学（例如锁屏后补传）
 *
 * 校验顺序有讲究：先查重（返回首次结果），再查序号，再查限流。
 * 理由见 evaluate 方法内的注释。
 */
public final class HeartbeatValidator {

    /** 客户端心跳间隔（秒） */
    public static final int HEARTBEAT_INTERVAL_SEC = 15;

    /** 单次心跳最多计入的秒数。超过按此值截断——防止「锁屏一小时」被算成有效学习 */
    public static final int MAX_DELTA_SEC = 60;

    /** 两次被接受的心跳之间的最小间隔（毫秒），用于限流 */
    public static final long MIN_ACCEPT_INTERVAL_MILLIS = 3_000L;

    private HeartbeatValidator() {
    }

    /**
     * 校验结果。
     *
     * @param accepted  是否接受本次心跳（accepted=false 时不更新会话累计）
     * @param acceptedSec 本次计入的有效秒数；OVER_DELTA 时为截断后的值，其余拒绝原因为 0
     * @param reason    被拒原因；accepted=true 时为 null
     */
    public record Decision(boolean accepted, int acceptedSec, HeartbeatRejectReason reason) {

        public static Decision accept(int acceptedSec) {
            return new Decision(true, acceptedSec, null);
        }

        public static Decision reject(HeartbeatRejectReason reason) {
            return new Decision(false, 0, reason);
        }

        /** 被截断但仍计入：这是唯一一种「拒绝原因非空但 accepted=true」的情况。 */
        public static Decision truncated(int acceptedSec) {
            return new Decision(true, acceptedSec, HeartbeatRejectReason.OVER_DELTA);
        }
    }

    /**
     * 判定一次心跳。
     *
     * 顺序说明：
     *   1) 先看序号 —— 重复序号（<= 已接受最大序号）意味着这是补传或重放。
     *      注意这里用「小于等于」而不是「等于」：小于已接受最大序号的是乱序包，
     *      同样不该重复计入，统一按重复处理。
     *   2) 再看限流 —— 间隔过短的请求直接丢弃，不给截断机会，否则脚本仍能靠高频积少成多。
     *   3) 最后看增量 —— 间隔合法但过长时截断，而不是整条丢弃。
     *      整条丢弃会让弱网用户（断网几分钟后恢复）永远拿不到学时，
     *      而截断只让作弊者拿到单个心跳上限的收益。
     *
     * @param clientSeq              本次心跳的会话内序号
     * @param deltaSec               客户端声称的本次间隔秒数
     * @param maxAcceptedSeq         会话内已接受的最大序号
     * @param millisSinceLastAccept  距上次被接受心跳的毫秒数；首次上报传 Long.MAX_VALUE
     */
    public static Decision evaluate(int clientSeq, int deltaSec, int maxAcceptedSeq,
                                    long millisSinceLastAccept) {
        if (clientSeq <= maxAcceptedSeq) {
            return Decision.reject(HeartbeatRejectReason.REPLAY);
        }
        if (deltaSec <= 0) {
            // 间隔非正数是客户端 bug 或伪造，直接丢弃
            return Decision.reject(HeartbeatRejectReason.OUT_OF_ORDER);
        }
        if (millisSinceLastAccept < MIN_ACCEPT_INTERVAL_MILLIS) {
            return Decision.reject(HeartbeatRejectReason.RATE_LIMITED);
        }
        if (deltaSec > MAX_DELTA_SEC) {
            return Decision.truncated(MAX_DELTA_SEC);
        }
        return Decision.accept(deltaSec);
    }
}
