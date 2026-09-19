package com.hongmai.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongmai.audit.service.AuditService;
import com.hongmai.common.enums.AuditAction;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.enums.ResourceForm;
import com.hongmai.common.enums.ResourceStatus;
import com.hongmai.common.enums.ResourceTheme;
import com.hongmai.common.enums.VrForm;
import com.hongmai.common.enums.VrTheme;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.web.LoginContext;
import com.hongmai.common.web.PageQuery;
import com.hongmai.common.web.PageResult;
import com.hongmai.content.domain.Resource;
import com.hongmai.content.domain.ResourceValidator;
import com.hongmai.content.dto.ResourceFilter;
import com.hongmai.content.dto.ResourceSaveCmd;
import com.hongmai.content.mapper.ResourceMapper;
import com.hongmai.content.service.ResourceService;
import com.hongmai.content.vo.ResourceCardVO;
import com.hongmai.content.vo.ResourceDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private static final String VR_EXPERIENCE_HINT =
            "该内容为 VR 沉浸式体验，需前往 VR 党建工作站完成。请在活动组织人员引导下佩戴 VR 眼镜参与。";

    private final ResourceMapper resourceMapper;
    private final AuditService auditService;

    @Override
    public PageResult<ResourceCardVO> pageForUser(long userId, PageQuery query, ResourceFilter filter) {
        LambdaQueryWrapper<Resource> wrapper = new LambdaQueryWrapper<Resource>()
                .eq(Resource::getStatus, ResourceStatus.PUBLISHED.getCode());

        if (filter != null) {
            wrapper.eq(filter.getForm() != null, Resource::getForm, filter.getForm())
                    .eq(filter.getTheme() != null, Resource::getTheme, filter.getTheme())
                    .eq(filter.getVrTheme() != null, Resource::getVrTheme, filter.getVrTheme())
                    .eq(filter.getVrForm() != null, Resource::getVrForm, filter.getVrForm());
            String keyword = trimToNull(filter.getKeyword());
            if (keyword != null) {
                wrapper.and(w -> w.like(Resource::getTitle, keyword)
                        .or().like(Resource::getSummary, keyword));
            }
        }
        wrapper.orderByDesc(Resource::getSortNo).orderByDesc(Resource::getId);

        Page<Resource> page = resourceMapper.selectPage(
                Page.of(query.getPageNo(), query.getPageSize()), wrapper);
        List<ResourceCardVO> records = page.getRecords().stream().map(this::toCard).toList();
        return PageResult.of(query.getPageNo(), query.getPageSize(), page.getTotal(), records);
    }

    @Override
    public ResourceDetailVO detailForUser(long userId, long resourceId) {
        Resource resource = resourceMapper.selectById(resourceId);
        // 未上架的资源对用户端等同于不存在 —— 不区分「不存在」与「未上架」，避免探测内部状态
        if (resource == null || resource.getStatus() == null
                || resource.getStatus() != ResourceStatus.PUBLISHED.getCode()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toDetail(resource);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long saveResource(long operatorId, ResourceSaveCmd cmd) {
        ResourceForm form = ResourceForm.of(cmd.getForm());
        List<String> violations = ResourceValidator.violations(form, cmd.getVrTheme(), cmd.getVrForm(),
                cmd.getContentUrl(), cmd.getSubtitleUrl(), cmd.getDurationSec());
        if (!violations.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, String.join("；", violations));
        }

        Resource entity = new Resource();
        entity.setId(cmd.getId());
        entity.setTitle(cmd.getTitle());
        entity.setSummary(cmd.getSummary());
        entity.setCoverUrl(cmd.getCoverUrl());
        entity.setForm(cmd.getForm());
        entity.setTheme(cmd.getTheme());
        entity.setVrTheme(form.isVr() ? cmd.getVrTheme() : null);
        entity.setVrForm(form.isVr() ? cmd.getVrForm() : null);
        entity.setContentUrl(form.isVr() ? null : cmd.getContentUrl());
        entity.setDurationSec(cmd.getDurationSec());
        entity.setSubtitleUrl(form.requiresSubtitle() ? cmd.getSubtitleUrl() : null);
        entity.setSubtitleLang(Resource.DEFAULT_SUBTITLE_LANG);
        entity.setSortNo(cmd.getSortNo() == null ? 0 : cmd.getSortNo());
        // 每次保存都回到「待审核」：编辑已上架资源同样需要重新过审，否则审核形同虚设
        entity.setStatus(ResourceStatus.PENDING.getCode());

        if (cmd.getId() == null) {
            entity.setCreatedBy(operatorId);
            resourceMapper.insert(entity);
        } else {
            Resource existing = resourceMapper.selectById(cmd.getId());
            if (existing == null) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            resourceMapper.updateById(entity);
        }

        long orgId = currentOrgId();
        auditService.submit(orgId, AuditBizType.RESOURCE, entity.getId(),
                "学习资源「" + entity.getTitle() + "」（" + form.getLabel() + "）提交审核", operatorId);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(long operatorId, long resourceId, ResourceStatus target, String remark) {
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 上架必须走审核流程（AuditService.review）。若允许从这里上架，
        // 会写出 pending=1 的假待办，且绕过审核记录 —— 这是流程漏洞，不是便捷。
        if (target == ResourceStatus.PUBLISHED) {
            throw new BizException(ErrorCode.AUDIT_ILLEGAL_TRANSITION,
                    "资源上架须通过审核流程，不能直接变更状态");
        }

        ResourceStatus from = ResourceStatus.of(resource.getStatus());
        if (!from.canTransitionTo(target)) {
            throw new BizException(ErrorCode.AUDIT_ILLEGAL_TRANSITION,
                    from.getLabel() + " 不允许迁移到 " + target.getLabel());
        }

        int rows = resourceMapper.migrateStatus(resourceId, from.getCode(), target.getCode());
        if (rows == 0) {
            throw new BizException(ErrorCode.AUDIT_ILLEGAL_TRANSITION, "资源状态已被其他操作变更");
        }

        // 两个目标都是终结动作：下架 → TAKE_DOWN，退回草稿 → REJECT。
        // 都必须把待审标记清掉（pending=0），否则待办队列会残留幽灵任务。
        AuditAction action = target == ResourceStatus.OFFLINE ? AuditAction.TAKE_DOWN : AuditAction.REJECT;
        auditService.record(currentOrgId(), AuditBizType.RESOURCE, resourceId,
                "学习资源「" + resource.getTitle() + "」变更为「" + target.getLabel() + "」",
                operatorId, action);
    }

    private ResourceCardVO toCard(Resource resource) {
        ResourceCardVO vo = new ResourceCardVO();
        vo.setId(resource.getId());
        vo.setTitle(resource.getTitle());
        vo.setSummary(resource.getSummary());
        vo.setCoverUrl(resource.getCoverUrl());
        vo.setForm(resource.getForm());
        vo.setFormLabel(formLabel(resource.getForm()));
        vo.setTheme(resource.getTheme());
        vo.setThemeLabel(themeLabel(resource.getTheme()));
        vo.setVrTheme(resource.getVrTheme());
        vo.setVrForm(resource.getVrForm());
        vo.setDurationSec(resource.getDurationSec());
        return vo;
    }

    private ResourceDetailVO toDetail(Resource resource) {
        ResourceDetailVO vo = new ResourceDetailVO();
        vo.setId(resource.getId());
        vo.setTitle(resource.getTitle());
        vo.setSummary(resource.getSummary());
        vo.setCoverUrl(resource.getCoverUrl());
        vo.setForm(resource.getForm());
        vo.setTheme(resource.getTheme());
        vo.setThemeLabel(themeLabel(resource.getTheme()));
        vo.setDurationSec(resource.getDurationSec());

        ResourceForm form;
        try {
            form = ResourceForm.of(resource.getForm());
        } catch (IllegalArgumentException e) {
            form = null;
        }
        vo.setFormLabel(form == null ? null : form.getLabel());

        if (form != null && form.isVr()) {
            // VR 类不给播放地址，只给到站引导
            vo.setContentUrl(null);
            vo.setExperienceHint(VR_EXPERIENCE_HINT);
            vo.setVrTheme(resource.getVrTheme());
            vo.setVrThemeLabel(vrThemeLabel(resource.getVrTheme()));
            vo.setVrForm(resource.getVrForm());
            vo.setVrFormLabel(vrFormLabel(resource.getVrForm()));
        } else {
            vo.setContentUrl(resource.getContentUrl());
            vo.setSubtitleUrl(resource.getSubtitleUrl());
            vo.setSubtitleLang(resource.getSubtitleLang());
        }
        return vo;
    }

    private String formLabel(Integer code) {
        try {
            return code == null ? null : ResourceForm.of(code).getLabel();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String themeLabel(Integer code) {
        try {
            return code == null ? null : ResourceTheme.of(code).getLabel();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String vrThemeLabel(Integer code) {
        try {
            return code == null ? null : VrTheme.of(code).getLabel();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String vrFormLabel(Integer code) {
        try {
            return code == null ? null : VrForm.of(code).getLabel();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private long currentOrgId() {
        LoginContext context = LoginContext.get();
        return context == null ? 0L : context.getPrimaryOrgId();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
