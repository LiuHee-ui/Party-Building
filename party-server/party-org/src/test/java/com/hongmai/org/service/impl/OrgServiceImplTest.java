package com.hongmai.org.service.impl;

import com.hongmai.common.enums.RoleType;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.web.LoginContext;
import com.hongmai.org.domain.Org;
import com.hongmai.org.mapper.OrgMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 组织服务测试。
 * 数据范围的三种语义必须严格区分——这是 spec AC4 的核心，也是最容易出越权的地方：
 *   null   = 不限范围（平台管理员）
 *   空列表  = 无任何可见组织
 *   非空   = 具体范围
 */
class OrgServiceImplTest {

    private OrgMapper orgMapper;
    private OrgServiceImpl service;

    @BeforeEach
    void setUp() {
        orgMapper = mock(OrgMapper.class);
        service = new OrgServiceImpl(orgMapper);
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    private void login(long userId, long orgId, RoleType... roles) {
        LoginContext context = new LoginContext();
        context.setUserId(userId);
        context.setPrimaryOrgId(orgId);
        Set<Integer> roleCodes = java.util.Arrays.stream(roles)
                .map(RoleType::getCode)
                .collect(java.util.stream.Collectors.toSet());
        context.setRoles(roleCodes);
        LoginContext.set(context);
    }

    private Org org(long id, String path, int status) {
        Org org = new Org();
        org.setId(id);
        org.setPath(path);
        org.setStatus(status);
        org.setLevel(3);
        return org;
    }

    // ---------- 数据范围 ----------

    @Test
    @DisplayName("无登录上下文时返回空，绝不放行")
    void noContextReturnsEmpty() {
        assertTrue(service.resolveVisibleOrgIds(1L).isEmpty());
    }

    @Test
    @DisplayName("平台管理员返回 null，表示不限范围")
    void platformAdminUnlimited() {
        login(1L, 3L, RoleType.PLATFORM_ADMIN);

        assertNull(service.resolveVisibleOrgIds(1L));
        verify(orgMapper, never()).selectIdsUnderPath(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("组织管理员按其组织路径前缀取子树")
    void orgAdminScopedByPath() {
        login(2L, 2L, RoleType.ORG_ADMIN);
        when(orgMapper.selectById(2L)).thenReturn(org(2L, "/1/2/", 1));
        when(orgMapper.selectIdsUnderPath("/1/2/")).thenReturn(List.of(2L, 3L, 4L));

        List<Long> visible = service.resolveVisibleOrgIds(2L);

        assertEquals(List.of(2L, 3L, 4L), visible);
    }

    @Test
    @DisplayName("未归属组织的账号返回空列表，而不是 null（null 会变成不限范围）")
    void userWithoutOrgGetsEmptyNotUnlimited() {
        login(3L, 0L, RoleType.USER);

        List<Long> visible = service.resolveVisibleOrgIds(3L);

        assertEquals(List.of(), visible);
        assertTrue(visible.isEmpty());
    }

    @Test
    @DisplayName("所属组织已停用时返回空列表")
    void disabledOrgReturnsEmpty() {
        login(4L, 5L, RoleType.ORG_ADMIN);
        when(orgMapper.selectById(5L)).thenReturn(org(5L, "/1/5/", 0));

        assertTrue(service.resolveVisibleOrgIds(4L).isEmpty());
    }

    @Test
    @DisplayName("所属组织不存在时返回空列表")
    void missingOrgReturnsEmpty() {
        login(5L, 9L, RoleType.ORG_ADMIN);
        when(orgMapper.selectById(9L)).thenReturn(null);

        assertTrue(service.resolveVisibleOrgIds(5L).isEmpty());
    }

    // ---------- 路径生成 ----------

    @Test
    @DisplayName("根组织路径为 /id/")
    void buildRootPath() {
        assertEquals("/1/", service.buildPath(1L, 0L));
    }

    @Test
    @DisplayName("子组织路径由上级 path 拼接")
    void buildChildPath() {
        when(orgMapper.selectById(2L)).thenReturn(org(2L, "/1/2/", 1));

        assertEquals("/1/2/35/", service.buildPath(35L, 2L));
    }

    @Test
    @DisplayName("上级组织不存在或停用时抛 4007")
    void buildPathWithMissingParentRejected() {
        when(orgMapper.selectById(7L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.buildPath(35L, 7L));
        assertEquals(ErrorCode.ORG_NOT_FOUND.getCode(), ex.getCode());

        when(orgMapper.selectById(8L)).thenReturn(org(8L, "/1/8/", 0));
        BizException disabled = assertThrows(BizException.class, () -> service.buildPath(35L, 8L));
        assertEquals(ErrorCode.ORG_NOT_FOUND.getCode(), disabled.getCode());
    }

    // ---------- 名称回填 ----------

    @Test
    @DisplayName("组织名回填为空入参时不查库")
    void namesOfEmptyReturnsEmptyMap() {
        assertTrue(service.namesOf(List.of()).isEmpty());
        assertTrue(service.namesOf(null).isEmpty());
        verify(orgMapper, never()).selectNamesByIds(org.mockito.ArgumentMatchers.anyList());
    }
}
