package com.hongmai.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongmai.audit.service.AuditService;
import com.hongmai.auth.domain.AdminApplication;
import com.hongmai.auth.domain.ProofUrlsCodec;
import com.hongmai.auth.dto.AdminApplyCmd;
import com.hongmai.auth.mapper.AdminApplicationMapper;
import com.hongmai.auth.service.AdminApplyService;
import com.hongmai.auth.vo.AdminApplyVO;
import com.hongmai.common.crypto.CryptoUtil;
import com.hongmai.common.crypto.MaskUtil;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.enums.AuditStatus;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.web.LoginContext;
import com.hongmai.common.web.PageQuery;
import com.hongmai.common.web.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminApplyServiceImpl implements AdminApplyService {

    private final AdminApplicationMapper applicationMapper;
    private final AuditService auditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long submit(long userId, AdminApplyCmd cmd) {
        // 已通过的不允许重复申请；已驳回的允许重新提交（覆盖更新而不是新增，
        // 因为表上有 uk_user 约束，且「一人一份有效申请」的语义比保留历史更重要）
        AdminApplication existing = applicationMapper.selectOne(
                new LambdaQueryWrapper<AdminApplication>().eq(AdminApplication::getUserId, userId));

        if (existing != null && existing.getStatus() != null
                && existing.getStatus() == AuditStatus.APPROVED.getCode()) {
            throw new BizException(ErrorCode.ADMIN_APPLY_EXISTS);
        }
        if (existing != null && existing.getStatus() != null
                && existing.getStatus() == AuditStatus.PENDING.getCode()) {
            throw new BizException(ErrorCode.ADMIN_APPLY_EXISTS);
        }

        long applyId;
        if (existing == null) {
            AdminApplication entity = new AdminApplication();
            entity.setUserId(userId);
            entity.setOrgId(cmd.getOrgId());
            entity.setRealName(cmd.getRealName());
            entity.setMobile(cmd.getMobile());
            entity.setMobileTail(MaskUtil.tail4(cmd.getMobile()));
            entity.setProofUrlsJson(ProofUrlsCodec.encode(cmd.getProofUrls()));
            entity.setStatus(AuditStatus.PENDING.getCode());
            applicationMapper.insert(entity);
            applyId = entity.getId();
        } else {
            AdminApplication update = new AdminApplication();
            update.setId(existing.getId());
            update.setOrgId(cmd.getOrgId());
            update.setRealName(cmd.getRealName());
            update.setMobile(cmd.getMobile());
            update.setMobileTail(MaskUtil.tail4(cmd.getMobile()));
            update.setProofUrlsJson(ProofUrlsCodec.encode(cmd.getProofUrls()));
            update.setStatus(AuditStatus.PENDING.getCode());
            update.setReviewerId(null);
            update.setReviewedAt(null);
            update.setRemark(null);
            applicationMapper.updateById(update);
            applyId = existing.getId();
        }

        auditService.submit(cmd.getOrgId(), AuditBizType.ADMIN_APPLY, applyId,
                cmd.getRealName() + "（手机尾号 " + MaskUtil.tail4(cmd.getMobile()) + "）申请管理员权限",
                userId);
        return applyId;
    }

    @Override
    public PageResult<AdminApplyVO> page(long operatorId, Integer status, PageQuery query) {
        LambdaQueryWrapper<AdminApplication> wrapper = new LambdaQueryWrapper<AdminApplication>()
                .eq(status != null, AdminApplication::getStatus, status)
                .orderByDesc(AdminApplication::getCreatedAt);

        applyDataScope(wrapper);

        Page<AdminApplication> page = applicationMapper.selectPage(
                Page.of(query.getPageNo(), query.getPageSize()), wrapper);
        List<AdminApplyVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(query.getPageNo(), query.getPageSize(), page.getTotal(), records);
    }

    /**
     * visibleOrgIds 为 null 表示不限范围；为空集时必须生成恒假条件，
     * 否则会退化成「查全部」——这是最典型的越权漏洞。
     */
    private void applyDataScope(LambdaQueryWrapper<AdminApplication> wrapper) {
        List<Long> visible = LoginContext.currentVisibleOrgIds();
        if (visible == null) {
            return;
        }
        if (visible.isEmpty()) {
            wrapper.eq(AdminApplication::getId, -1L);
            return;
        }
        wrapper.in(AdminApplication::getOrgId, visible);
    }

    private AdminApplyVO toVO(AdminApplication entity) {
        AdminApplyVO vo = new AdminApplyVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setRealName(entity.getRealName());
        vo.setMobileTail(entity.getMobileTail());
        vo.setOrgId(entity.getOrgId());
        vo.setProofUrls(entity.proofUrls());
        vo.setStatus(entity.getStatus());
        vo.setStatusLabel(statusLabel(entity.getStatus()));
        vo.setRemark(entity.getRemark());
        vo.setSubmittedAt(entity.getCreatedAt());
        vo.setReviewedAt(entity.getReviewedAt());
        return vo;
    }

    private String statusLabel(Integer status) {
        if (status == null) {
            return null;
        }
        try {
            return AuditStatus.of(status).getLabel();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void review(long operatorId, long applyId, boolean pass, String remark) {
        // 委托 AuditService，保证流水写入路径唯一
        auditService.review(operatorId, AuditBizType.ADMIN_APPLY, applyId, pass, remark);
    }

    /** 供排障使用：某用户的手机号哈希，确认哈希是确定性可查的。 */
    static String mobileHashOf(String mobile) {
        return CryptoUtil.deterministicHash(mobile);
    }
}
