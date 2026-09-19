package com.hongmai.audit.service;

import com.hongmai.audit.vo.AuditTaskVO;
import com.hongmai.common.enums.AuditAction;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.web.PageQuery;
import com.hongmai.common.web.PageResult;

/**
 * 审核服务。
 *
 * 写入路径唯一性约束：t_audit_log 只有 record 与 review 两个写入入口，
 * 业务模块不得自行写这张表——这条约束消除了「同一动作两条写入路径」的可能。
 */
public interface AuditService {

    /**
     * 落一条审核流水。由业务模块在自身状态变更后调用。
     *
     * @param action SUBMIT 时 pending=1（进入待审队列）；APPROVE / REJECT / TAKE_DOWN 时 pending=0
     */
    void record(long orgId, AuditBizType bizType, long bizId, String summary, long operatorId, AuditAction action);

    /** 提交审核的便捷入口，等价于 action=SUBMIT。 */
    default void submit(long orgId, AuditBizType bizType, long bizId, String summary, long operatorId) {
        record(orgId, bizType, bizId, summary, operatorId, AuditAction.SUBMIT);
    }

    /**
     * 审核。查 AuditableHandler(bizType) 并回调 migrate，
     * 成功后落 APPROVE / REJECT 流水并把该 bizId 的 pending 置 0。
     *
     * @throws com.hongmai.common.exception.BizException 4004 状态不允许该迁移；驳回时 remark 为空
     */
    void review(long operatorId, AuditBizType bizType, long bizId, boolean pass, String remark);

    /** 待审队列分页。数据范围取 LoginContext 的可见组织，仅查 pending=1。 */
    PageResult<AuditTaskVO> pagePending(long operatorId, AuditBizType bizType, PageQuery query);
}
