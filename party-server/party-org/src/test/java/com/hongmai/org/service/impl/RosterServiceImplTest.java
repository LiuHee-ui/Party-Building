package com.hongmai.org.service.impl;

import com.hongmai.auth.spi.UserRosterProvider;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.web.PageQuery;
import com.hongmai.common.web.PageResult;
import com.hongmai.org.domain.Org;
import com.hongmai.org.dto.RosterQuery;
import com.hongmai.org.mapper.OrgMapper;
import com.hongmai.org.service.OrgService;
import com.hongmai.org.vo.EthnicityStatVO;
import com.hongmai.org.vo.RosterItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 名册服务测试。
 * 重点：指定组织必须落在可见范围内，越界要明确拒绝而不是返回空列表——
 * 返回空列表会让调用方误以为「该组织确实没人」，掩盖越权尝试。
 */
class RosterServiceImplTest {

    private OrgService orgService;
    private OrgMapper orgMapper;
    private UserRosterProvider provider;
    private RosterServiceImpl service;

    @BeforeEach
    void setUp() {
        orgService = mock(OrgService.class);
        orgMapper = mock(OrgMapper.class);
        provider = mock(UserRosterProvider.class);
        service = new RosterServiceImpl(orgService, orgMapper, provider);
    }

    private Org org(long id, String path) {
        Org org = new Org();
        org.setId(id);
        org.setPath(path);
        org.setName("支部" + id);
        org.setStatus(1);
        org.setLevel(3);
        return org;
    }

    private UserRosterProvider.RosterRow row(long userId, long orgId, String ethnicity) {
        return new UserRosterProvider.RosterRow(userId, "党员" + userId, "8000",
                orgId, 1, ethnicity, 0);
    }

    // ---------- 分页范围 ----------

    @Test
    @DisplayName("不指定组织时按可见范围查询")
    void defaultScopeUsesVisibleOrgs() {
        when(orgService.resolveVisibleOrgIds(1L)).thenReturn(List.of(2L, 3L));
        when(provider.pageRoster(eq(List.of(2L, 3L)), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(PageResult.of(1, 20, 1, List.of(row(10L, 3L, "藏族"))));
        when(orgService.namesOf(List.of(3L))).thenReturn(java.util.Map.of(3L, "第一村党支部"));

        PageResult<RosterItemVO> page = service.pageRoster(1L, new RosterQuery());

        assertEquals(1, page.getTotal());
        assertEquals("第一村党支部", page.getRecords().get(0).getOrgName());
        assertEquals("正式党员", page.getRecords().get(0).getIdentityTypeLabel());
    }

    @Test
    @DisplayName("指定组织在可见范围内时按该组织子树查询")
    void requestedOrgWithinScope() {
        when(orgService.resolveVisibleOrgIds(1L)).thenReturn(List.of(2L, 3L, 4L));
        when(orgService.requireEnabled(2L)).thenReturn(org(2L, "/1/2/"));
        when(orgMapper.selectIdsUnderPath("/1/2/")).thenReturn(List.of(2L, 3L, 4L));
        when(provider.pageRoster(eq(List.of(2L, 3L, 4L)), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(PageResult.empty(1, 20));

        service.pageRoster(1L, queryOfOrg(2L));

        verify(provider).pageRoster(eq(List.of(2L, 3L, 4L)), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("指定组织完全在可见范围之外时报 3001，而不是返回空列表")
    void requestedOrgOutsideScopeRejected() {
        when(orgService.resolveVisibleOrgIds(1L)).thenReturn(List.of(3L));
        when(orgService.requireEnabled(99L)).thenReturn(org(99L, "/1/99/"));
        when(orgMapper.selectIdsUnderPath("/1/99/")).thenReturn(List.of(99L, 100L));

        RosterQuery query = queryOfOrg(99L);
        BizException ex = assertThrows(BizException.class, () -> service.pageRoster(1L, query));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verify(provider, never()).pageRoster(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("可见组织为空时不得退化为查全部")
    void emptyVisibleScopeBlocksQuery() {
        when(orgService.resolveVisibleOrgIds(1L)).thenReturn(List.of());
        when(provider.pageRoster(eq(List.of()), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(PageResult.empty(1, 20));

        PageResult<RosterItemVO> page = service.pageRoster(1L, new RosterQuery());

        assertEquals(0, page.getTotal());
        verify(provider).pageRoster(eq(List.of()), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("平台管理员（visible=null）指定任意组织都放行")
    void platformAdminUnlimited() {
        when(orgService.resolveVisibleOrgIds(1L)).thenReturn(null);
        when(orgService.requireEnabled(99L)).thenReturn(org(99L, "/9/99/"));
        when(orgMapper.selectIdsUnderPath("/9/99/")).thenReturn(List.of(99L));
        when(provider.pageRoster(eq(List.of(99L)), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(PageResult.empty(1, 20));

        service.pageRoster(1L, queryOfOrg(99L));

        verify(provider).pageRoster(eq(List.of(99L)), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("检索词被 trim，空白串转成 null")
    void blankKeywordNormalizedToNull() {
        when(orgService.resolveVisibleOrgIds(1L)).thenReturn(List.of(3L));
        when(provider.pageRoster(any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(PageResult.empty(1, 20));

        RosterQuery query = new RosterQuery();
        query.setKeyword("   ");
        query.setEthnicity("  ");
        service.pageRoster(1L, query);

        verify(provider).pageRoster(eq(List.of(3L)), isNull(), isNull(), isNull(), eq(true), any());
    }

    // ---------- 民族统计 ----------

    @Test
    @DisplayName("占比按范围内总人数计算，且与明细口径一致")
    void ethnicityRatioComputed() {
        when(orgService.resolveVisibleOrgIds(1L)).thenReturn(List.of(3L));
        when(orgService.requireEnabled(3L)).thenReturn(org(3L, "/1/2/3/"));
        when(orgMapper.selectIdsUnderPath("/1/2/3/")).thenReturn(List.of(3L));
        when(provider.countByEthnicity(List.of(3L))).thenReturn(List.of(
                new UserRosterProvider.EthnicityCount("藏族", 3),
                new UserRosterProvider.EthnicityCount("彝族", 2),
                new UserRosterProvider.EthnicityCount("汉族", 5)));

        List<EthnicityStatVO> stats = service.statEthnicity(1L, 3L);

        assertEquals(3, stats.size());
        assertEquals(0, new BigDecimal("30.00").compareTo(stats.get(0).getRatio()));
        assertEquals("30.00%", stats.get(0).getRatioText());
        assertEquals(0, new BigDecimal("50.00").compareTo(stats.get(2).getRatio()));
    }

    @Test
    @DisplayName("无数据时返回空列表，不抛异常")
    void ethnicityEmptyWhenNoData() {
        when(orgService.resolveVisibleOrgIds(1L)).thenReturn(List.of(3L));
        when(orgService.requireEnabled(3L)).thenReturn(org(3L, "/1/2/3/"));
        when(orgMapper.selectIdsUnderPath("/1/2/3/")).thenReturn(List.of(3L));
        when(provider.countByEthnicity(List.of(3L))).thenReturn(List.of());

        assertTrue(service.statEthnicity(1L, 3L).isEmpty());
    }

    @Test
    @DisplayName("orgId 传 0 视为不指定，使用可见范围")
    void zeroOrgIdMeansDefaultScope() {
        when(orgService.resolveVisibleOrgIds(1L)).thenReturn(List.of(3L));
        when(provider.countByEthnicity(List.of(3L))).thenReturn(List.of());

        service.statEthnicity(1L, 0L);

        verify(provider).countByEthnicity(List.of(3L));
        verify(orgService, never()).requireEnabled(anyLong());
    }

    @Test
    @DisplayName("统计的 orgId 越界同样报 3001")
    void ethnicityOutsideScopeRejected() {
        when(orgService.resolveVisibleOrgIds(1L)).thenReturn(List.of(3L));
        when(orgService.requireEnabled(99L)).thenReturn(org(99L, "/1/99/"));
        when(orgMapper.selectIdsUnderPath("/1/99/")).thenReturn(List.of(99L));

        BizException ex = assertThrows(BizException.class, () -> service.statEthnicity(1L, 99L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    private RosterQuery queryOfOrg(long orgId) {
        RosterQuery query = new RosterQuery();
        query.setOrgId(orgId);
        return query;
    }
}
