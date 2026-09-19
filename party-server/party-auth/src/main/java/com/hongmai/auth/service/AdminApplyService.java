package com.hongmai.auth.service;

import com.hongmai.auth.dto.AdminApplyCmd;
import com.hongmai.auth.vo.AdminApplyVO;
import com.hongmai.common.web.PageQuery;
import com.hongmai.common.web.PageResult;

public interface AdminApplyService {

    /**
     * 提交管理员注册申请。校验该用户不存在有效申请（待审核或已通过），
     * 落库 status=待审核 并经 AuditService 落 SUBMIT 流水。
     *
     * @throws com.hongmai.common.exception.BizException 4005 已存在待审核或已通过的申请
     */
    long submit(long userId, AdminApplyCmd cmd);

    /** 平台管理员分页查看申请。status 不传表示全部。 */
    PageResult<AdminApplyVO> page(long operatorId, Integer status, PageQuery query);

    /**
     * 审核申请。委托 AuditService 执行；通过时由处理器向 t_user_role 授予管理类角色。
     * pass=false 时 remark 必填。
     */
    void review(long operatorId, long applyId, boolean pass, String remark);
}
