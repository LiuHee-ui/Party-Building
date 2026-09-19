package com.hongmai.content.service.impl;

import com.hongmai.audit.service.AuditService;
import com.hongmai.common.enums.AuditAction;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.enums.ResourceStatus;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.content.domain.Resource;
import com.hongmai.content.dto.ResourceSaveCmd;
import com.hongmai.content.mapper.ResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * 资源服务测试。
 * 重点验证三条容易出错的规则：
 *   1. 保存后一律回到「待审核」——编辑已上架资源也要重新过审
 *   2. VR 资源不落内容地址（spec F5）
 *   3. 上架不能走状态变更接口，必须走审核流程
 */
class ResourceServiceImplTest {

    private ResourceMapper resourceMapper;
    private AuditService auditService;
    private ResourceServiceImpl service;

    @BeforeEach
    void setUp() {
        resourceMapper = mock(ResourceMapper.class);
        auditService = mock(AuditService.class);
        service = new ResourceServiceImpl(resourceMapper, auditService);
    }

    private ResourceSaveCmd videoCmd() {
        ResourceSaveCmd cmd = new ResourceSaveCmd();
        cmd.setTitle("飞夺泸定桥");
        cmd.setForm(2);
        cmd.setTheme(1);
        cmd.setContentUrl("https://cdn/v.mp4");
        cmd.setSubtitleUrl("https://cdn/v.vtt");
        cmd.setDurationSec(600);
        return cmd;
    }

    private Resource resource(long id, int status) {
        Resource r = new Resource();
        r.setId(id);
        r.setTitle("资源" + id);
        r.setForm(2);
        r.setStatus(status);
        return r;
    }

    // ---------- 保存 ----------

    @Test
    @DisplayName("新增资源：落库为待审核并提交审核流水")
    void saveCreatesPendingResource() {
        when(resourceMapper.insert(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(77L);
            return 1;
        });

        long id = service.saveResource(9L, videoCmd());

        assertEquals(77L, id);
        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceMapper).insert(captor.capture());
        Resource saved = captor.getValue();
        assertEquals(ResourceStatus.PENDING.getCode(), saved.getStatus());
        assertEquals("zh-Hans", saved.getSubtitleLang());
        assertEquals(9L, saved.getCreatedBy());

        verify(auditService).submit(anyLong(), eq(AuditBizType.RESOURCE), eq(77L), anyString(), eq(9L));
    }

    @Test
    @DisplayName("编辑已上架资源同样回到待审核——否则审核形同虚设")
    void editingPublishedResourceReturnsToPending() {
        ResourceSaveCmd cmd = videoCmd();
        cmd.setId(50L);
        when(resourceMapper.selectById(50L)).thenReturn(resource(50L, ResourceStatus.PUBLISHED.getCode()));

        service.saveResource(9L, cmd);

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceMapper).updateById(captor.capture());
        assertEquals(ResourceStatus.PENDING.getCode(), captor.getValue().getStatus());
    }

    @Test
    @DisplayName("VR 资源不落内容地址与字幕，即使入参传了")
    void vrResourceStripsContentAndSubtitle() {
        ResourceSaveCmd cmd = new ResourceSaveCmd();
        cmd.setTitle("血战湘江");
        cmd.setForm(4);
        cmd.setTheme(1);
        cmd.setVrTheme(1);
        cmd.setVrForm(1);
        when(resourceMapper.insert(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(88L);
            return 1;
        });

        service.saveResource(9L, cmd);

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceMapper).insert(captor.capture());
        assertNull(captor.getValue().getContentUrl());
        assertNull(captor.getValue().getSubtitleUrl());
    }

    @Test
    @DisplayName("非 VR 资源不落 VR 分类字段")
    void nonVrResourceStripsVrFields() {
        ResourceSaveCmd cmd = videoCmd();
        cmd.setVrTheme(1);
        cmd.setVrForm(2);
        when(resourceMapper.insert(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(90L);
            return 1;
        });

        service.saveResource(9L, cmd);

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceMapper).insert(captor.capture());
        assertNull(captor.getValue().getVrTheme());
        assertNull(captor.getValue().getVrForm());
    }

    @Test
    @DisplayName("字段校验不通过时不落库、不提交审核")
    void invalidFieldsRejectedBeforePersist() {
        ResourceSaveCmd cmd = videoCmd();
        cmd.setSubtitleUrl(null);   // 视频缺字幕

        BizException ex = assertThrows(BizException.class, () -> service.saveResource(9L, cmd));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), ex.getCode());
        verify(resourceMapper, never()).insert(any(Resource.class));
        verify(auditService, never()).submit(anyLong(), any(), anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("编辑不存在的资源报 4006")
    void editingMissingResourceRejected() {
        ResourceSaveCmd cmd = videoCmd();
        cmd.setId(404L);
        when(resourceMapper.selectById(404L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.saveResource(9L, cmd));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), ex.getCode());
    }

    // ---------- 状态变更 ----------

    @Test
    @DisplayName("上架不能走状态变更接口，必须走审核流程")
    void publishViaChangeStatusRejected() {
        when(resourceMapper.selectById(10L)).thenReturn(resource(10L, ResourceStatus.PENDING.getCode()));

        BizException ex = assertThrows(BizException.class,
                () -> service.changeStatus(9L, 10L, ResourceStatus.PUBLISHED, null));

        assertEquals(ErrorCode.AUDIT_ILLEGAL_TRANSITION.getCode(), ex.getCode());
        verify(resourceMapper, never()).migrateStatus(anyLong(), anyInt(), anyInt());
        verify(auditService, never()).record(anyLong(), any(), anyLong(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("下架：迁移状态并落 TAKE_DOWN 流水，清掉待审标记")
    void takeDownWritesAuditLog() {
        when(resourceMapper.selectById(10L)).thenReturn(resource(10L, ResourceStatus.PUBLISHED.getCode()));
        when(resourceMapper.migrateStatus(10L, ResourceStatus.PUBLISHED.getCode(),
                ResourceStatus.OFFLINE.getCode())).thenReturn(1);

        service.changeStatus(9L, 10L, ResourceStatus.OFFLINE, "内容过时");

        verify(auditService).record(anyLong(), eq(AuditBizType.RESOURCE), eq(10L), anyString(),
                eq(9L), eq(AuditAction.TAKE_DOWN));
    }

    @Test
    @DisplayName("非法迁移被拒绝（草稿不能直接下架）")
    void illegalTransitionRejected() {
        when(resourceMapper.selectById(10L)).thenReturn(resource(10L, ResourceStatus.DRAFT.getCode()));

        BizException ex = assertThrows(BizException.class,
                () -> service.changeStatus(9L, 10L, ResourceStatus.OFFLINE, null));

        assertEquals(ErrorCode.AUDIT_ILLEGAL_TRANSITION.getCode(), ex.getCode());
        verify(resourceMapper, never()).migrateStatus(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("并发下影响 0 行说明状态已被改动，报 4004")
    void concurrentStatusChangeDetected() {
        when(resourceMapper.selectById(10L)).thenReturn(resource(10L, ResourceStatus.PUBLISHED.getCode()));
        when(resourceMapper.migrateStatus(anyLong(), anyInt(), anyInt())).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> service.changeStatus(9L, 10L, ResourceStatus.OFFLINE, null));

        assertEquals(ErrorCode.AUDIT_ILLEGAL_TRANSITION.getCode(), ex.getCode());
        verify(auditService, never()).record(anyLong(), any(), anyLong(), anyString(), anyLong(), any());
    }

    // ---------- 用户端详情 ----------

    @Test
    @DisplayName("未上架资源对用户端等同于不存在")
    void unpublishedResourceHidden() {
        when(resourceMapper.selectById(10L)).thenReturn(resource(10L, ResourceStatus.PENDING.getCode()));

        BizException ex = assertThrows(BizException.class, () -> service.detailForUser(1L, 10L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("VR 资源详情不给播放地址，给到站引导")
    void vrDetailHidesContentUrl() {
        Resource vr = new Resource();
        vr.setId(11L);
        vr.setTitle("VR 血战湘江");
        vr.setForm(4);
        vr.setVrTheme(1);
        vr.setVrForm(1);
        vr.setContentUrl("不应出现");
        vr.setStatus(ResourceStatus.PUBLISHED.getCode());
        when(resourceMapper.selectById(11L)).thenReturn(vr);

        var detail = service.detailForUser(1L, 11L);

        assertNull(detail.getContentUrl());
        assertEquals("革命战争", detail.getVrThemeLabel());
        assertEquals("漫游类", detail.getVrFormLabel());
        org.junit.jupiter.api.Assertions.assertNotNull(detail.getExperienceHint());
    }

    @Test
    @DisplayName("视频资源详情返回字幕与内容地址")
    void videoDetailExposesSubtitle() {
        Resource v = new Resource();
        v.setId(12L);
        v.setTitle("飞夺泸定桥");
        v.setForm(2);
        v.setTheme(1);
        v.setContentUrl("https://cdn/v.mp4");
        v.setSubtitleUrl("https://cdn/v.vtt");
        v.setSubtitleLang("zh-Hans");
        v.setStatus(ResourceStatus.PUBLISHED.getCode());
        when(resourceMapper.selectById(12L)).thenReturn(v);

        var detail = service.detailForUser(1L, 12L);

        assertEquals("https://cdn/v.mp4", detail.getContentUrl());
        assertEquals("https://cdn/v.vtt", detail.getSubtitleUrl());
        assertEquals("zh-Hans", detail.getSubtitleLang());
        assertNull(detail.getExperienceHint());
    }
}
