package com.hongmai.auth.controller;

import com.hongmai.auth.dto.AdminApplyCmd;
import com.hongmai.auth.dto.AdminApplyReviewCmd;
import com.hongmai.auth.service.AdminApplyService;
import com.hongmai.auth.vo.AdminApplyVO;
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

@RestController
@RequestMapping("/admin/apply")
@RequiredArgsConstructor
public class AdminApplyController {

    private final AdminApplyService adminApplyService;

    /** 提交管理员注册申请。未授权的账号进入管理端后被引导到这里。 */
    @PostMapping
    public R<Long> submit(@RequestBody @Valid AdminApplyCmd cmd) {
        return R.ok(adminApplyService.submit(LoginContext.currentUserId(), cmd));
    }

    /** 申请列表。status 不传表示全部。 */
    @GetMapping("/page")
    public R<PageResult<AdminApplyVO>> page(@RequestParam(required = false) Integer status,
                                            @Valid PageQuery query) {
        return R.ok(adminApplyService.page(LoginContext.currentUserId(), status, query));
    }

    /** 审核申请。通过后由处理器授予管理类角色。 */
    @PostMapping("/review")
    public R<Void> review(@RequestBody @Valid AdminApplyReviewCmd cmd) {
        adminApplyService.review(LoginContext.currentUserId(), cmd.getApplyId(),
                cmd.getPass(), cmd.getRemark());
        return R.ok();
    }
}
