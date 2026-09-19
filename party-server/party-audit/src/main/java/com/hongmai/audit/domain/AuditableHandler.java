package com.hongmai.audit.domain;

import com.hongmai.common.enums.AuditAction;
import com.hongmai.common.enums.AuditBizType;

/**
 * 可审对象的能力接口（依赖倒置点）。
 *
 * 为什么这么设计：t_audit_log 是唯一的审核流水落点，但状态迁移要发生在不同模块的表上
 * ——资源表在 party-content、用户表在 party-auth、评论表在 party-interact。
 * 如果让 party-audit 反向依赖这些业务模块去改状态，依赖图就成环了，
 * 而且每新增一种可审对象都要改 audit 模块。
 *
 * 所以由 party-audit 定义接口，业务模块实现并注册为 Spring Bean，
 * audit 在审核时回调，不关心实现方是谁。新增可审对象时 audit 模块零改动。
 *
 * 实现方须自行校验合法迁移，非法迁移抛 BizException(AUDIT_ILLEGAL_TRANSITION)。
 */
public interface AuditableHandler {

    /** 本处理器负责的可审对象类型，实现方必须唯一。 */
    AuditBizType bizType();

    /**
     * 迁移业务状态。实现方负责在**同一事务内**更新业务表并校验迁移合法性。
     *
     * @param bizId      业务主键
     * @param action     APPROVE / REJECT（review 阶段）或 TAKE_DOWN（终态动作）
     * @param remark     审核意见，REJECT 时不为空
     * @param operatorId 审核人，实现方需要落 reviewer_id 时使用。
     *                   显式传参而不是从 LoginContext 取——让处理器不依赖请求上下文，便于单测
     */
    void migrate(long bizId, AuditAction action, String remark, long operatorId);
}
