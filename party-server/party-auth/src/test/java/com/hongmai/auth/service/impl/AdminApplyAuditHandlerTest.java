package com.hongmai.auth.service.impl;

import com.hongmai.auth.domain.AdminApplication;
import com.hongmai.auth.domain.UserRole;
import com.hongmai.auth.mapper.AdminApplicationMapper;
import com.hongmai.auth.mapper.UserRoleMapper;
import com.hongmai.common.enums.AuditAction;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.enums.AuditStatus;
import com.hongmai.common.enums.RoleType;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.spi.OrgLevelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员申请审核处理器测试。
 * 重点验证：授予的管理员级别按组织层级决定，且组织层级能力缺失时降级而不是提权。
 */
class AdminApplyAuditHandlerTest {

    private AdminApplicationMapper applicationMapper;
    private UserRoleMapper userRoleMapper;
    private ObjectProvider<OrgLevelProvider> orgLevelProvider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        applicationMapper = mock(AdminApplicationMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        orgLevelProvider = mock(ObjectProvider.class);
    }

    private AdminApplyAuditHandler handler() {
        return new AdminApplyAuditHandler(applicationMapper, userRoleMapper, orgLevelProvider);
    }

    private AdminApplication application(long orgId) {
        AdminApplication app = new AdminApplication();
        app.setId(1L);
        app.setUserId(50L);
        app.setOrgId(orgId);
        app.setStatus(AuditStatus.PENDING.getCode());
        return app;
    }

    private void stubMigrateSuccess() {
        when(applicationMapper.selectById(1L)).thenReturn(application(2L));
        when(applicationMapper.migrateStatus(anyLong(), anyInt(), anyLong(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("处理器负责的类型是管理员申请")
    void bizTypeIsAdminApply() {
        assertEquals(AuditBizType.ADMIN_APPLY, handler().bizType());
    }

    @Test
    @DisplayName("党支部申请通过：授予组织管理员")
    void approveGrantsOrgAdminForBranch() {
        stubMigrateSuccess();
        when(orgLevelProvider.getIfAvailable()).thenReturn(orgId -> 3);
        when(userRoleMapper.countByUserAndRole(50L, RoleType.ORG_ADMIN.getCode())).thenReturn(0);

        handler().migrate(1L, AuditAction.APPROVE, null, 99L);

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(captor.capture());
        assertEquals(RoleType.ORG_ADMIN.getCode(), captor.getValue().getRole());
        assertEquals(50L, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("党委申请通过：授予上级组织管理员")
    void approveGrantsSuperiorAdminForCommittee() {
        stubMigrateSuccess();
        when(orgLevelProvider.getIfAvailable()).thenReturn(orgId -> 1);
        when(userRoleMapper.countByUserAndRole(50L, RoleType.SUPERIOR_ORG_ADMIN.getCode())).thenReturn(0);

        handler().migrate(1L, AuditAction.APPROVE, null, 99L);

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(captor.capture());
        assertEquals(RoleType.SUPERIOR_ORG_ADMIN.getCode(), captor.getValue().getRole());
    }

    @Test
    @DisplayName("组织层级能力缺失时降级为组织管理员，不放行更高权限")
    void missingOrgLevelProviderFallsBackToOrgAdmin() {
        stubMigrateSuccess();
        when(orgLevelProvider.getIfAvailable()).thenReturn(null);
        when(userRoleMapper.countByUserAndRole(50L, RoleType.ORG_ADMIN.getCode())).thenReturn(0);

        handler().migrate(1L, AuditAction.APPROVE, null, 99L);

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(captor.capture());
        assertEquals(RoleType.ORG_ADMIN.getCode(), captor.getValue().getRole());
    }

    @Test
    @DisplayName("已持有该角色时不重复插入")
    void existingRoleNotDuplicated() {
        stubMigrateSuccess();
        when(orgLevelProvider.getIfAvailable()).thenReturn(orgId -> 3);
        when(userRoleMapper.countByUserAndRole(50L, RoleType.ORG_ADMIN.getCode())).thenReturn(1);

        handler().migrate(1L, AuditAction.APPROVE, null, 99L);

        verify(userRoleMapper, never()).insert(any(UserRole.class));
    }

    @Test
    @DisplayName("驳回不授予任何角色")
    void rejectGrantsNothing() {
        stubMigrateSuccess();

        handler().migrate(1L, AuditAction.REJECT, "证明材料不清晰", 99L);

        verify(userRoleMapper, never()).insert(any(UserRole.class));
        verify(applicationMapper).migrateStatus(1L, AuditStatus.REJECTED.getCode(), 99L, "证明材料不清晰");
    }

    @Test
    @DisplayName("影响行数为 0 说明已被处理，报 4004 且不授予角色")
    void zeroRowsRejected() {
        when(applicationMapper.selectById(1L)).thenReturn(application(2L));
        when(applicationMapper.migrateStatus(anyLong(), anyInt(), anyLong(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> handler().migrate(1L, AuditAction.APPROVE, null, 99L));

        assertEquals(ErrorCode.AUDIT_ILLEGAL_TRANSITION.getCode(), ex.getCode());
        verify(userRoleMapper, never()).insert(any(UserRole.class));
    }

    @Test
    @DisplayName("申请不存在时报 4004")
    void missingApplicationRejected() {
        when(applicationMapper.selectById(404L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> handler().migrate(404L, AuditAction.APPROVE, null, 99L));

        assertEquals(ErrorCode.AUDIT_ILLEGAL_TRANSITION.getCode(), ex.getCode());
    }
}
