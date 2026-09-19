package com.hongmai.auth.service.impl;

import com.hongmai.auth.mapper.UserMapper;
import com.hongmai.common.enums.AuditAction;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.enums.AuditStatus;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实名审核处理器测试。
 * 重点验证只有「待审核」能被审核——靠 SQL 的 audit_status=0 条件保证并发安全，
 * 影响行数为 0 时必须是 4004，不能静默成功。
 */
class RealNameAuditHandlerTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final RealNameAuditHandler handler = new RealNameAuditHandler(userMapper);

    @Test
    @DisplayName("处理器负责的类型是用户实名")
    void bizTypeIsRealName() {
        assertEquals(AuditBizType.REAL_NAME, handler.bizType());
    }

    @Test
    @DisplayName("通过：迁移到已通过")
    void approveMigratesToApproved() {
        when(userMapper.migrateAuditStatus(anyLong(), anyInt(), any())).thenReturn(1);

        handler.migrate(10L, AuditAction.APPROVE, null, 99L);

        verify(userMapper).migrateAuditStatus(10L, AuditStatus.APPROVED.getCode(), null);
    }

    @Test
    @DisplayName("驳回：迁移到已驳回并带上审核意见")
    void rejectMigratesWithRemark() {
        when(userMapper.migrateAuditStatus(anyLong(), anyInt(), anyString())).thenReturn(1);

        handler.migrate(10L, AuditAction.REJECT, "材料不清晰", 99L);

        verify(userMapper).migrateAuditStatus(10L, AuditStatus.REJECTED.getCode(), "材料不清晰");
    }

    @Test
    @DisplayName("影响行数为 0 说明不在待审核状态，报 4004")
    void zeroRowsIsIllegalTransition() {
        when(userMapper.migrateAuditStatus(anyLong(), anyInt(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> handler.migrate(10L, AuditAction.APPROVE, null, 99L));

        assertEquals(ErrorCode.AUDIT_ILLEGAL_TRANSITION.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("不支持的动作（如下架）报 4004，且不碰数据库")
    void unsupportedActionRejected() {
        BizException ex = assertThrows(BizException.class,
                () -> handler.migrate(10L, AuditAction.TAKE_DOWN, null, 99L));

        assertEquals(ErrorCode.AUDIT_ILLEGAL_TRANSITION.getCode(), ex.getCode());
        verify(userMapper, never()).migrateAuditStatus(anyLong(), anyInt(), any());
    }
}
