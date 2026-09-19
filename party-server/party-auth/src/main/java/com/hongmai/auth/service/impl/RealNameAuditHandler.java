package com.hongmai.auth.service.impl;

import com.hongmai.audit.domain.AuditableHandler;
import com.hongmai.auth.mapper.UserMapper;
import com.hongmai.common.enums.AuditAction;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.enums.AuditStatus;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 实名审核处理器。
 *
 * 只允许 待审核 → 通过 / 驳回。SQL 里带了 audit_status = 0 条件，
 * 因此并发重复审核时第二次影响行数为 0，会被识别为非法迁移——
 * 这比「先查状态再更新」在并发下更可靠。
 */
@Component
@RequiredArgsConstructor
public class RealNameAuditHandler implements AuditableHandler {

    private final UserMapper userMapper;

    @Override
    public AuditBizType bizType() {
        return AuditBizType.REAL_NAME;
    }

    @Override
    public void migrate(long bizId, AuditAction action, String remark, long operatorId) {
        int toStatus = switch (action) {
            case APPROVE -> AuditStatus.APPROVED.getCode();
            case REJECT -> AuditStatus.REJECTED.getCode();
            default -> throw new BizException(ErrorCode.AUDIT_ILLEGAL_TRANSITION,
                    "实名审核不支持动作 " + action.getCode());
        };

        int rows = userMapper.migrateAuditStatus(bizId, toStatus, remark);
        if (rows == 0) {
            throw new BizException(ErrorCode.AUDIT_ILLEGAL_TRANSITION,
                    "该实名申请不处于待审核状态，或已被处理");
        }
    }
}
