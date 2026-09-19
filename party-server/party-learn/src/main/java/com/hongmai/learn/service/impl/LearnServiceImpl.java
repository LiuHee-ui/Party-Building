package com.hongmai.learn.service.impl;

import com.hongmai.common.enums.HeartbeatRejectReason;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.util.DateUtil;
import com.hongmai.common.web.LoginContext;
import com.hongmai.learn.domain.CreditMath;
import com.hongmai.learn.domain.FocusCalculator;
import com.hongmai.learn.domain.HeartbeatValidator;
import com.hongmai.learn.domain.LearnHeartbeat;
import com.hongmai.learn.domain.LearnRecord;
import com.hongmai.learn.domain.LearnSession;
import com.hongmai.learn.dto.HeartbeatCmd;
import com.hongmai.learn.mapper.LearnHeartbeatMapper;
import com.hongmai.learn.mapper.LearnRecordMapper;
import com.hongmai.learn.mapper.LearnSessionMapper;
import com.hongmai.learn.service.CreditService;
import com.hongmai.learn.service.LearnService;
import com.hongmai.learn.vo.ReportResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearnServiceImpl implements LearnService {

    private final LearnSessionMapper sessionMapper;
    private final LearnHeartbeatMapper heartbeatMapper;
    private final LearnRecordMapper recordMapper;
    private final CreditService creditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportResultVO reportHeartbeat(long userId, HeartbeatCmd cmd) {
        LearnSession session = sessionMapper.selectBySessionId(cmd.getSessionId());
        if (session == null) {
            session = createSession(userId, cmd);
        } else if (!Long.valueOf(userId).equals(session.getUserId())) {
            // 会话归属校验：拿到别人的 sessionId 不能替他刷学时
            throw new BizException(ErrorCode.FORBIDDEN, "会话不属于当前用户");
        }

        // 幂等：断网补传会重发同一条心跳，直接返回首次结果，不重复计入
        Integer priorAccepted = heartbeatMapper.selectAcceptedSec(cmd.getSessionId(), cmd.getClientSeq());
        if (priorAccepted != null) {
            return buildReplayResult(session, priorAccepted);
        }

        int maxAcceptedSeq = session.getLastSeq() == null ? 0 : session.getLastSeq();
        long millisSinceLast = millisSince(session.getLastEndAt());
        HeartbeatValidator.Decision decision = HeartbeatValidator.evaluate(
                cmd.getClientSeq(), cmd.getDeltaSec(), maxAcceptedSeq, millisSinceLast);

        if (!decision.accepted()) {
            recordHeartbeat(session, cmd, 0, decision.reason());
            log.debug("心跳被拒 sessionId={} seq={} reason={}",
                    cmd.getSessionId(), cmd.getClientSeq(), decision.reason());
            return buildResult(session, 0, decision.reason(),
                    creditService.accrue(userId, session.getOrgId(), cmd.getResourceId(),
                            DateUtil.today(), 0));
        }

        // 失焦口径：累计失焦超过 600 秒后才扣超出部分
        int accumulatedUnfocused = session.getUnfocusedSec() == null ? 0 : session.getUnfocusedSec();
        int effectiveDelta = FocusCalculator.effectiveDeltaSec(
                cmd.getFocusedSec(), decision.acceptedSec(), accumulatedUnfocused);
        int unfocusedThisRound = FocusCalculator.unfocusedSecOf(decision.acceptedSec(), cmd.getFocusedSec());

        recordHeartbeat(session, cmd, effectiveDelta, decision.reason());

        // 推进会话累计。带 last_seq 条件，并发下只有序号更大的请求能推进
        int advanced = sessionMapper.advance(cmd.getSessionId(), cmd.getClientSeq(), DateUtil.now(),
                effectiveDelta, unfocusedThisRound);
        if (advanced == 0) {
            // 并发心跳比本请求序号大，本请求的累计已被覆盖，但仍保留心跳明细与学时
            log.debug("会话推进被更高序号请求抢先 sessionId={} seq={}", cmd.getSessionId(), cmd.getClientSeq());
        }

        int newSessionValidSec = (session.getValidSec() == null ? 0 : session.getValidSec()) + effectiveDelta;
        advanceRecord(userId, session.getOrgId(), cmd, effectiveDelta);

        CreditService.AccrualResult accrual = creditService.accrue(
                userId, session.getOrgId(), cmd.getResourceId(), DateUtil.today(), effectiveDelta);

        return buildResult(session, effectiveDelta, decision.reason(), accrual);
    }

    private LearnSession createSession(long userId, HeartbeatCmd cmd) {
        LearnSession session = new LearnSession();
        session.setSessionId(cmd.getSessionId());
        session.setUserId(userId);
        session.setResourceId(cmd.getResourceId());
        session.setOrgId(currentOrgId());
        session.setBeginAt(DateUtil.now());
        session.setLastEndAt(null);
        session.setLastSeq(0);
        session.setUnfocusedSec(0);
        session.setValidSec(0);
        session.setSource(1);
        session.setReportedAt(DateUtil.now());
        sessionMapper.insert(session);
        return session;
    }

    /** 落心跳明细。uk_session_seq 是幂等的最终保障——并发下重复插入会被唯一键挡住。 */
    private void recordHeartbeat(LearnSession session, HeartbeatCmd cmd, int acceptedSec,
                                 HeartbeatRejectReason reason) {
        LearnHeartbeat heartbeat = new LearnHeartbeat();
        heartbeat.setSessionId(cmd.getSessionId());
        heartbeat.setClientSeq(cmd.getClientSeq());
        heartbeat.setDeltaSec(cmd.getDeltaSec());
        heartbeat.setFocusedSec(cmd.getFocusedSec());
        heartbeat.setPositionSec(cmd.getPositionSec() == null ? 0 : cmd.getPositionSec());
        heartbeat.setAcceptedSec(acceptedSec);
        heartbeat.setRejectReason(reason == null ? null : reason.getCode());
        heartbeat.setReportedAt(DateUtil.now());
        heartbeatMapper.insert(heartbeat);
    }

    /**
     * 推进学习记录（用户 × 资源聚合）。记录不存在时先建。
     *
     * 进度口径用**记录累计**（用户 × 资源）而不是会话累计：
     * 完成度描述的是「这份资源学到哪了」，跨会话、跨天都要延续，
     * 用会话累计会导致第二天重看时进度从零开始。
     */
    private void advanceRecord(long userId, long orgId, HeartbeatCmd cmd, int effectiveDelta) {
        LearnRecord record = recordMapper.selectByUserResource(userId, cmd.getResourceId());
        LocalDateTime now = DateUtil.now();

        if (record == null) {
            LearnRecord entity = new LearnRecord();
            entity.setUserId(userId);
            entity.setOrgId(orgId);
            entity.setResourceId(cmd.getResourceId());
            entity.setLastPositionSec(cmd.getPositionSec() == null ? 0 : cmd.getPositionSec());
            entity.setProgressPct(progressOf(effectiveDelta, cmd.getDurationSec()));
            entity.setValidSec(effectiveDelta);
            entity.setUnreadFlag(0);
            entity.setFirstStartAt(now);
            entity.setLastLearnAt(now);
            recordMapper.insert(entity);
            return;
        }

        int newValidSec = (record.getValidSec() == null ? 0 : record.getValidSec()) + effectiveDelta;
        recordMapper.advance(userId, cmd.getResourceId(), effectiveDelta,
                cmd.getPositionSec() == null ? 0 : cmd.getPositionSec(),
                progressOf(newValidSec, cmd.getDurationSec()), now);
    }

    private BigDecimal progressOf(int validSec, Integer durationSec) {
        if (durationSec == null || durationSec <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal pct = BigDecimal.valueOf(validSec)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(durationSec), 2, RoundingMode.HALF_UP);
        return pct.compareTo(BigDecimal.valueOf(100)) > 0
                ? BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP) : pct;
    }

    /** 距上次被接受心跳的毫秒数；从未接受过则返回 MAX_VALUE，使首次心跳不受限流影响。 */
    private long millisSince(LocalDateTime lastEndAt) {
        if (lastEndAt == null) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, Duration.between(lastEndAt, DateUtil.now()).toMillis());
    }

    private ReportResultVO buildResult(LearnSession session, int acceptedSec,
                                       HeartbeatRejectReason reason,
                                       CreditService.AccrualResult accrual) {
        ReportResultVO vo = new ReportResultVO();
        vo.setAccepted(true);
        vo.setAcceptedSec(acceptedSec);
        vo.setRejectReason(reason == null ? null : reason.getCode());
        vo.setSessionValidSec((session.getValidSec() == null ? 0 : session.getValidSec()) + acceptedSec);
        vo.setTodayMinutes(accrual.todayMinutes());
        vo.setDailyCapReached(accrual.capReached());
        vo.setTodayCredit(accrual.todayCredit());
        return vo;
    }

    /** 重复上报：返回首次的处理结果，accepted 标记为 false 便于客户端区分。 */
    private ReportResultVO buildReplayResult(LearnSession session, int priorAcceptedSec) {
        ReportResultVO vo = new ReportResultVO();
        vo.setAccepted(false);
        vo.setAcceptedSec(priorAcceptedSec);
        vo.setRejectReason(HeartbeatRejectReason.REPLAY.getCode());
        vo.setSessionValidSec(session.getValidSec() == null ? 0 : session.getValidSec());
        vo.setTodayCredit(CreditMath.toCredit(0));
        vo.setDailyCapReached(false);
        return vo;
    }

    private long currentOrgId() {
        LoginContext context = LoginContext.get();
        return context == null ? 0L : context.getPrimaryOrgId();
    }
}
