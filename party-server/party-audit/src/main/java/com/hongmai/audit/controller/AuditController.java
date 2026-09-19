package com.hongmai.audit.controller;

import com.hongmai.audit.dto.AuditReviewCmd;
import com.hongmai.audit.service.AuditService;
import com.hongmai.audit.vo.AuditTaskVO;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.web.LoginContext;
import com.hongmai.common.web.PageQuery;
import com.hongmai.common.web.PageResult;
import com.hongmai.common.web.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 审核中心接口。操作员身份一律取自 LoginContext，不接受客户端传入。 */
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /** 待审队列。bizType 不传表示查全部类型。 */
    @GetMapping("/pending")
    public R<PageResult<AuditTaskVO>> pagePending(@RequestParam(required = false) Integer bizType,
                                                  @Valid PageQuery query) {
        AuditBizType type = bizType == null ? null : AuditBizType.of(bizType);
        return R.ok(auditService.pagePending(LoginContext.currentUserId(), type, query));
    }

    /** 审核。 */
    @PostMapping("/review")
    public R<Void> review(@RequestBody @Valid AuditReviewCmd cmd) {
        auditService.review(LoginContext.currentUserId(), AuditBizType.of(cmd.getBizType()),
                cmd.getBizId(), cmd.getPass(), cmd.getRemark());
        return R.ok();
    }
}
