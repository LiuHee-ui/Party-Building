package com.hongmai.content.service.impl;

import com.hongmai.audit.domain.AuditableHandler;
import com.hongmai.common.enums.AuditAction;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.enums.ResourceStatus;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.content.mapper.ResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 学习资源审核处理器。
 *
 * 只允许 待审核 → 已上架 / 草稿。迁移 SQL 带 status=待审核 条件，
 * 并发重复审核时第二次影响 0 行，被识别为非法迁移。
 */
@Component
@RequiredArgsConstructor
public class ResourceAuditHandler implements AuditableHandler {

    private final ResourceMapper resourceMapper;

    @Override
    public AuditBizType bizType() {
        return AuditBizType.RESOURCE;
    }

    @Override
    public void migrate(long bizId, AuditAction action, String remark, long operatorId) {
        ResourceStatus target = switch (action) {
            case APPROVE -> ResourceStatus.PUBLISHED;
            case REJECT -> ResourceStatus.DRAFT;
            default -> throw new BizException(ErrorCode.AUDIT_ILLEGAL_TRANSITION,
                    "学习资源审核不支持动作 " + action.getCode());
        };

        int rows = resourceMapper.migrateStatus(bizId, ResourceStatus.PENDING.getCode(), target.getCode());
        if (rows == 0) {
            throw new BizException(ErrorCode.AUDIT_ILLEGAL_TRANSITION,
                    "该资源不处于待审核状态，或已被处理");
        }
    }
}
