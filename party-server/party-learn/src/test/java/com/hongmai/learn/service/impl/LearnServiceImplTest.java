package com.hongmai.learn.service.impl;

import com.hongmai.common.enums.HeartbeatRejectReason;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.web.LoginContext;
import com.hongmai.learn.domain.LearnHeartbeat;
import com.hongmai.learn.domain.LearnRecord;
import com.hongmai.learn.domain.LearnSession;
import com.hongmai.learn.dto.HeartbeatCmd;
import com.hongmai.learn.mapper.LearnHeartbeatMapper;
import com.hongmai.learn.mapper.LearnRecordMapper;
import com.hongmai.learn.mapper.LearnSessionMapper;
import com.hongmai.learn.service.CreditService;
import com.hongmai.learn.vo.ReportResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 心跳上报链路测试。
 * 这是全项目逻辑最密的一条链路：幂等 → 校验 → 失焦 → 会话推进 → 记录推进 → 学时折算。
 * 用 mock 隔离数据库，专注验证编排顺序与参数传递是否正确。
 */
class LearnServiceImplTest {

    private LearnSessionMapper sessionMapper;
    private LearnHeartbeatMapper heartbeatMapper;
    private LearnRecordMapper recordMapper;
    private CreditService creditService;
    private LearnServiceImpl service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(LearnSessionMapper.class);
        heartbeatMapper = mock(LearnHeartbeatMapper.class);
        recordMapper = mock(LearnRecordMapper.class);
        creditService = mock(CreditService.class);
        service = new LearnServiceImpl(sessionMapper, heartbeatMapper, recordMapper, creditService);

        LoginContext context = new LoginContext();
        context.setUserId(1L);
        context.setPrimaryOrgId(3L);
        LoginContext.set(context);
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    private HeartbeatCmd cmd(int seq, int delta, int focused) {
        HeartbeatCmd cmd = new HeartbeatCmd();
        cmd.setSessionId("sess-1");
        cmd.setResourceId(7L);
        cmd.setClientSeq(seq);
        cmd.setDeltaSec(delta);
        cmd.setFocusedSec(focused);
        cmd.setPositionSec(30);
        cmd.setDurationSec(600);
        return cmd;
    }

    private LearnSession existingSession(long userId, int lastSeq, int unfocusedSec,
                                         LocalDateTime lastEndAt) {
        LearnSession session = new LearnSession();
        session.setId(10L);
        session.setSessionId("sess-1");
        session.setUserId(userId);
        session.setResourceId(7L);
        session.setOrgId(3L);
        session.setLastSeq(lastSeq);
        session.setUnfocusedSec(unfocusedSec);
        session.setValidSec(100);
        session.setLastEndAt(lastEndAt);
        return session;
    }

    private void stubAccrue() {
        when(creditService.accrue(anyLong(), anyLong(), anyLong(), any(), anyInt()))
                .thenReturn(new CreditService.AccrualResult(15, 15, new BigDecimal("0.33"), false));
    }

    // ---------- 会话归属 ----------

    @Test
    @DisplayName("会话属于他人时报 3001，不得替他刷学时")
    void sessionOfAnotherUserRejected() {
        when(sessionMapper.selectBySessionId("sess-1"))
                .thenReturn(existingSession(999L, 0, 0, LocalDateTime.now().minusMinutes(1)));

        BizException ex = assertThrows(BizException.class,
                () -> service.reportHeartbeat(1L, cmd(1, 15, 15)));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verify(heartbeatMapper, never()).insert(any(LearnHeartbeat.class));
    }

    @Test
    @DisplayName("首次上报自动建档，组织取自登录态")
    void firstReportCreatesSession() {
        when(sessionMapper.selectBySessionId("sess-1")).thenReturn(null);
        when(sessionMapper.insert(any(LearnSession.class))).thenAnswer(inv -> {
            LearnSession s = inv.getArgument(0);
            s.setId(11L);
            return 1;
        });
        when(recordMapper.selectByUserResource(1L, 7L)).thenReturn(null);
        stubAccrue();

        service.reportHeartbeat(1L, cmd(1, 15, 15));

        ArgumentCaptor<LearnSession> captor = ArgumentCaptor.forClass(LearnSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertEquals(3L, captor.getValue().getOrgId());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals(0, captor.getValue().getLastSeq());
    }

    // ---------- 幂等 ----------

    @Test
    @DisplayName("重复上报返回首次结果，不重复折算学时")
    void replayReturnsFirstResultWithoutAccruing() {
        when(sessionMapper.selectBySessionId("sess-1"))
                .thenReturn(existingSession(1L, 5, 0, LocalDateTime.now()));
        when(heartbeatMapper.selectAcceptedSec("sess-1", 3)).thenReturn(12);

        ReportResultVO result = service.reportHeartbeat(1L, cmd(3, 15, 15));

        assertFalse(result.isAccepted(), "重放标记为未接受，便于客户端识别");
        assertEquals(12, result.getAcceptedSec(), "必须是首次认定的秒数");
        assertEquals(HeartbeatRejectReason.REPLAY.getCode(), result.getRejectReason());
        verify(heartbeatMapper, never()).insert(any(LearnHeartbeat.class));
        verify(creditService, never()).accrue(anyLong(), anyLong(), anyLong(), any(), anyInt());
    }

    // ---------- 被拒心跳 ----------

    @Test
    @DisplayName("限流心跳仍落明细并记录原因，便于离线取证")
    void rateLimitedHeartbeatRecordedWithReason() {
        when(sessionMapper.selectBySessionId("sess-1"))
                .thenReturn(existingSession(1L, 5, 0, LocalDateTime.now()));
        when(heartbeatMapper.selectAcceptedSec("sess-1", 6)).thenReturn(null);
        when(creditService.accrue(anyLong(), anyLong(), anyLong(), any(), anyInt()))
                .thenReturn(new CreditService.AccrualResult(0, 0, BigDecimal.ZERO, false));

        service.reportHeartbeat(1L, cmd(6, 15, 15));

        ArgumentCaptor<LearnHeartbeat> captor = ArgumentCaptor.forClass(LearnHeartbeat.class);
        verify(heartbeatMapper).insert(captor.capture());
        assertEquals(HeartbeatRejectReason.RATE_LIMITED.getCode(), captor.getValue().getRejectReason());
        assertEquals(0, captor.getValue().getAcceptedSec());
        verify(sessionMapper, never()).advance(anyString(), anyInt(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("序号回退被判重放，不推进会话")
    void outOfOrderSeqRejected() {
        when(sessionMapper.selectBySessionId("sess-1"))
                .thenReturn(existingSession(1L, 9, 0, LocalDateTime.now().minusMinutes(1)));
        when(heartbeatMapper.selectAcceptedSec("sess-1", 4)).thenReturn(null);
        when(creditService.accrue(anyLong(), anyLong(), anyLong(), any(), anyInt()))
                .thenReturn(new CreditService.AccrualResult(0, 0, BigDecimal.ZERO, false));

        service.reportHeartbeat(1L, cmd(4, 15, 15));

        verify(sessionMapper, never()).advance(anyString(), anyInt(), any(), anyInt(), anyInt());
    }

    // ---------- 正常链路 ----------

    @Test
    @DisplayName("正常心跳：推进会话、推进记录、按有效秒数折算学时")
    void acceptedHeartbeatAdvancesEverything() {
        when(sessionMapper.selectBySessionId("sess-1"))
                .thenReturn(existingSession(1L, 1, 0, LocalDateTime.now().minusSeconds(20)));
        when(heartbeatMapper.selectAcceptedSec("sess-1", 2)).thenReturn(null);
        when(recordMapper.selectByUserResource(1L, 7L)).thenReturn(null);
        stubAccrue();

        ReportResultVO result = service.reportHeartbeat(1L, cmd(2, 15, 15));

        assertTrue(result.isAccepted());
        assertEquals(15, result.getAcceptedSec());

        verify(sessionMapper).advance(eq("sess-1"), eq(2), any(), eq(15), eq(0));
        verify(creditService).accrue(eq(1L), eq(3L), eq(7L), any(LocalDate.class), eq(15));

        ArgumentCaptor<LearnRecord> recordCaptor = ArgumentCaptor.forClass(LearnRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        assertEquals(0, recordCaptor.getValue().getUnreadFlag());
        assertEquals(15, recordCaptor.getValue().getValidSec());
        // 15 秒 / 600 秒 = 2.50%
        assertEquals(0, new BigDecimal("2.50").compareTo(recordCaptor.getValue().getProgressPct()));
    }

    @Test
    @DisplayName("超长间隔按 60 秒截断，不按声称的一小时折算")
    void overDeltaTruncatedBeforeAccrual() {
        when(sessionMapper.selectBySessionId("sess-1"))
                .thenReturn(existingSession(1L, 1, 0, LocalDateTime.now().minusHours(1)));
        when(heartbeatMapper.selectAcceptedSec("sess-1", 2)).thenReturn(null);
        when(recordMapper.selectByUserResource(1L, 7L)).thenReturn(new LearnRecord());
        stubAccrue();

        ReportResultVO result = service.reportHeartbeat(1L, cmd(2, 3600, 3600));

        assertEquals(60, result.getAcceptedSec(), "锁屏一小时最多计入 60 秒");
        verify(creditService).accrue(anyLong(), anyLong(), anyLong(), any(), eq(60));
    }

    @Test
    @DisplayName("失焦超过豁免额度后，有效秒数被扣减")
    void unfocusBeyondForgiveReducesAcceptedSec() {
        // 累计失焦已达 700 秒，超出豁免 100 秒；本次聚焦 5 秒、间隔 15 秒 → 有效 0
        when(sessionMapper.selectBySessionId("sess-1"))
                .thenReturn(existingSession(1L, 1, 700, LocalDateTime.now().minusSeconds(20)));
        when(heartbeatMapper.selectAcceptedSec("sess-1", 2)).thenReturn(null);
        when(recordMapper.selectByUserResource(1L, 7L)).thenReturn(new LearnRecord());
        stubAccrue();

        ReportResultVO result = service.reportHeartbeat(1L, cmd(2, 15, 5));

        assertEquals(0, result.getAcceptedSec());
        verify(creditService).accrue(anyLong(), anyLong(), anyLong(), any(), eq(0));
    }

    @Test
    @DisplayName("失焦在豁免额度内时不影响有效秒数")
    void unfocusWithinForgiveKeepsFullSec() {
        when(sessionMapper.selectBySessionId("sess-1"))
                .thenReturn(existingSession(1L, 1, 300, LocalDateTime.now().minusSeconds(20)));
        when(heartbeatMapper.selectAcceptedSec("sess-1", 2)).thenReturn(null);
        when(recordMapper.selectByUserResource(1L, 7L)).thenReturn(new LearnRecord());
        stubAccrue();

        ReportResultVO result = service.reportHeartbeat(1L, cmd(2, 15, 10));

        assertEquals(10, result.getAcceptedSec(), "豁免额度内失焦不扣，只计聚焦的 10 秒");
        // 本次新增失焦 5 秒，累计到 305
        verify(sessionMapper).advance(eq("sess-1"), eq(2), any(), eq(10), eq(5));
    }

    @Test
    @DisplayName("已有学习记录时走更新而不是新建")
    void existingRecordIsUpdated() {
        LearnRecord existing = new LearnRecord();
        existing.setId(88L);
        existing.setValidSec(120);
        when(sessionMapper.selectBySessionId("sess-1"))
                .thenReturn(existingSession(1L, 1, 0, LocalDateTime.now().minusSeconds(20)));
        when(heartbeatMapper.selectAcceptedSec("sess-1", 2)).thenReturn(null);
        when(recordMapper.selectByUserResource(1L, 7L)).thenReturn(existing);
        stubAccrue();

        service.reportHeartbeat(1L, cmd(2, 15, 15));

        verify(recordMapper, never()).insert(any(LearnRecord.class));
        verify(recordMapper).advance(eq(1L), eq(7L), eq(15), eq(30), any(), any());
    }

    @Test
    @DisplayName("封顶状态透传给客户端")
    void capReachedPropagated() {
        when(sessionMapper.selectBySessionId("sess-1"))
                .thenReturn(existingSession(1L, 1, 0, LocalDateTime.now().minusSeconds(20)));
        when(heartbeatMapper.selectAcceptedSec("sess-1", 2)).thenReturn(null);
        when(recordMapper.selectByUserResource(1L, 7L)).thenReturn(new LearnRecord());
        when(creditService.accrue(anyLong(), anyLong(), anyLong(), any(), anyInt()))
                .thenReturn(new CreditService.AccrualResult(120, 0, new BigDecimal("2.67"), true));

        ReportResultVO result = service.reportHeartbeat(1L, cmd(2, 15, 15));

        assertTrue(result.isDailyCapReached());
        assertEquals(120, result.getTodayMinutes());
    }

    @Test
    @DisplayName("每次上报都会写一条心跳明细——这是防刷取证的唯一来源")
    void everyHeartbeatRecorded() {
        when(sessionMapper.selectBySessionId("sess-1"))
                .thenReturn(existingSession(1L, 1, 0, LocalDateTime.now().minusSeconds(20)));
        when(heartbeatMapper.selectAcceptedSec(anyString(), anyInt())).thenReturn(null);
        when(recordMapper.selectByUserResource(anyLong(), anyLong())).thenReturn(new LearnRecord());
        stubAccrue();

        service.reportHeartbeat(1L, cmd(2, 15, 15));
        service.reportHeartbeat(1L, cmd(3, 15, 15));

        verify(heartbeatMapper, org.mockito.Mockito.times(2)).insert(any(LearnHeartbeat.class));
    }

    @Test
    @DisplayName("无登录上下文时组织为 0，不抛异常（定时任务等场景）")
    void noLoginContextDoesNotBreak() {
        LoginContext.clear();
        when(sessionMapper.selectBySessionId("sess-1")).thenReturn(null);
        when(sessionMapper.insert(any(LearnSession.class))).thenAnswer(inv -> {
            LearnSession s = inv.getArgument(0);
            s.setId(12L);
            return 1;
        });
        when(recordMapper.selectByUserResource(1L, 7L)).thenReturn(null);
        stubAccrue();

        service.reportHeartbeat(1L, cmd(1, 15, 15));

        ArgumentCaptor<LearnSession> captor = ArgumentCaptor.forClass(LearnSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertEquals(0L, captor.getValue().getOrgId());
        assertNotNull(captor.getValue().getSessionId());
    }
}
