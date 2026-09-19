package com.hongmai.audit.service.impl;

import com.hongmai.audit.domain.AuditLog;
import com.hongmai.audit.domain.AuditableHandler;
import com.hongmai.audit.mapper.AuditLogMapper;
import com.hongmai.common.enums.AuditAction;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审核服务测试。
 * 重点验证依赖倒置的回调链、状态迁移失败必须回滚、以及驳回时审核意见必填。
 */
class AuditServiceImplTest {

    private AuditLogMapper mapper;
    private RecordingHandler handler;

    /** 记录被调用情况的假处理器，替代真实的业务模块实现。 */
    static class RecordingHandler implements AuditableHandler {

        final List<AuditAction> calls = new ArrayList<>();
        RuntimeException toThrow;

        @Override
        public AuditBizType bizType() {
            return AuditBizType.RESOURCE;
        }

        @Override
        public void migrate(long bizId, AuditAction action, String remark, long operatorId) {
            if (toThrow != null) {
                throw toThrow;
            }
            calls.add(action);
        }
    }

    @BeforeEach
    void setUp() {
        mapper = mock(AuditLogMapper.class);
        handler = new RecordingHandler();
    }

    private AuditServiceImpl newService(AuditableHandler... handlers) {
        return new AuditServiceImpl(mapper, List.of(handlers));
    }

    @Test
    @DisplayName("审核通过：回调业务迁移状态、清待审标记、写入 APPROVE 流水")
    void approveHappyPath() {
        when(mapper.clearPending(anyInt(), anyLong())).thenReturn(1);
        AuditServiceImpl service = newService(handler);

        service.review(99L, AuditBizType.RESOURCE, 7L, true, null);

        assertEquals(List.of(AuditAction.APPROVE), handler.calls);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper).insert(captor.capture());
        AuditLog written = captor.getValue();
        assertEquals("APPROVE", written.getAction());
        assertEquals(0, written.getPending());
        assertEquals(99L, written.getOperatorId());
        assertEquals(7L, written.getBizId());
        assertEquals(AuditBizType.RESOURCE.getCode(), written.getBizType());
    }

    @Test
    @DisplayName("驳回时必须填写审核意见")
    void rejectWithoutRemarkRejected() {
        AuditServiceImpl service = newService(handler);

        BizException ex = assertThrows(BizException.class,
                () -> service.review(99L, AuditBizType.RESOURCE, 7L, false, "  "));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), ex.getCode());
        assertTrue(handler.calls.isEmpty(), "参数不合法时不应触发状态迁移");
        verify(mapper, never()).insert(any(AuditLog.class));
    }

    @Test
    @DisplayName("驳回时写入 REJECT 流水并带审核意见")
    void rejectWritesRemark() {
        when(mapper.clearPending(anyInt(), anyLong())).thenReturn(1);
        AuditServiceImpl service = newService(handler);

        service.review(99L, AuditBizType.RESOURCE, 7L, false, "材料不完整");

        assertEquals(List.of(AuditAction.REJECT), handler.calls);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper).insert(captor.capture());
        assertEquals("REJECT", captor.getValue().getAction());
        assertEquals("材料不完整", captor.getValue().getRemark());
    }

    @Test
    @DisplayName("业务模块抛非法迁移时异常向上传播，不吞掉")
    void illegalTransitionPropagates() {
        handler.toThrow = new BizException(ErrorCode.AUDIT_ILLEGAL_TRANSITION);
        AuditServiceImpl service = newService(handler);

        BizException ex = assertThrows(BizException.class,
                () -> service.review(99L, AuditBizType.RESOURCE, 7L, true, null));

        assertEquals(ErrorCode.AUDIT_ILLEGAL_TRANSITION.getCode(), ex.getCode());
        verify(mapper, never()).clearPending(anyInt(), anyLong());
        verify(mapper, never()).insert(any(AuditLog.class));
    }

    @Test
    @DisplayName("状态迁移成功但无待审任务时抛 4004，暴露数据不一致")
    void missingPendingTaskRollsBack() {
        when(mapper.clearPending(anyInt(), anyLong())).thenReturn(0);
        AuditServiceImpl service = newService(handler);

        BizException ex = assertThrows(BizException.class,
                () -> service.review(99L, AuditBizType.RESOURCE, 7L, true, null));

        assertEquals(ErrorCode.AUDIT_ILLEGAL_TRANSITION.getCode(), ex.getCode());
        verify(mapper, never()).insert(any(AuditLog.class));
    }

    @Test
    @DisplayName("未注册可审类型时报错，而不是静默通过")
    void unregisteredBizTypeRejected() {
        AuditServiceImpl service = newService(handler);

        BizException ex = assertThrows(BizException.class,
                () -> service.review(99L, AuditBizType.ADMIN_APPLY, 7L, true, null));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("同一可审类型注册多个处理器时启动即失败")
    void duplicateHandlerRejectedAtStartup() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> newService(new RecordingHandler(), new RecordingHandler()));

        assertTrue(ex.getMessage().contains("注册了多个处理器"));
    }

    @Test
    @DisplayName("提交审核写入 pending=1 的 SUBMIT 流水")
    void submitWritesPendingLog() {
        AuditServiceImpl service = newService(handler);

        service.submit(2L, AuditBizType.RESOURCE, 7L, "《飞夺泸定桥》VR 内容", 99L);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper).insert(captor.capture());
        AuditLog written = captor.getValue();
        assertEquals("SUBMIT", written.getAction());
        assertEquals(1, written.getPending());
        assertEquals(2L, written.getOrgId());
        assertNotNull(written.getSummary());
    }

    @Test
    @DisplayName("下架等终态动作不进入待审队列")
    void terminalActionNotPending() {
        AuditServiceImpl service = newService(handler);

        service.record(2L, AuditBizType.RESOURCE, 7L, "下架", 99L, AuditAction.TAKE_DOWN);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper).insert(captor.capture());
        assertEquals("TAKE_DOWN", captor.getValue().getAction());
        assertEquals(0, captor.getValue().getPending());
    }

    @Test
    @DisplayName("摘要超长时截断到 200 字，避免写库失败")
    void summaryTruncated() {
        AuditServiceImpl service = newService(handler);

        service.submit(2L, AuditBizType.RESOURCE, 7L, "甲".repeat(500), 99L);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper).insert(captor.capture());
        assertEquals(200, captor.getValue().getSummary().length());
    }

    @Test
    @DisplayName("审核通过只落一条流水，不重复写")
    void singleLogPerReview() {
        when(mapper.clearPending(anyInt(), anyLong())).thenReturn(1);
        AuditServiceImpl service = newService(handler);

        service.review(99L, AuditBizType.RESOURCE, 7L, true, null);

        verify(mapper, times(1)).insert(any(AuditLog.class));
        verify(mapper, times(1)).clearPending(AuditBizType.RESOURCE.getCode(), 7L);
    }
}
