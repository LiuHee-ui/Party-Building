package com.hongmai.auth.controller;

import com.hongmai.auth.dto.LoginCmd;
import com.hongmai.auth.dto.RealNameCmd;
import com.hongmai.auth.dto.RealNameReviewCmd;
import com.hongmai.auth.service.AuthService;
import com.hongmai.auth.vo.LoginResult;
import com.hongmai.auth.vo.RealNameApplyVO;
import com.hongmai.common.enums.EntryType;
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
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 微信登录。白名单接口，不需要登录态。 */
    @PostMapping("/login")
    public R<LoginResult> login(@RequestBody @Valid LoginCmd cmd) {
        return R.ok(authService.loginByWechat(cmd.getJsCode()));
    }

    /** 提交实名信息。 */
    @PostMapping("/realname")
    public R<Void> submitRealName(@RequestBody @Valid RealNameCmd cmd) {
        authService.submitRealName(LoginContext.currentUserId(), cmd);
        return R.ok();
    }

    /** 待审核实名申请列表。 */
    @GetMapping("/realname/pending")
    public R<PageResult<RealNameApplyVO>> pageRealNameApply(@Valid PageQuery query) {
        return R.ok(authService.pageRealNameApply(LoginContext.currentUserId(), query));
    }

    /** 审核实名申请。 */
    @PostMapping("/realname/review")
    public R<Void> reviewRealName(@RequestBody @Valid RealNameReviewCmd cmd) {
        authService.reviewRealName(LoginContext.currentUserId(), cmd.getUserId(),
                cmd.getPass(), cmd.getRemark());
        return R.ok();
    }

    /** 进入指定入口前的鉴权。前端在切入口时调用，提前把无权限的账号拦在门外。 */
    @GetMapping("/entry/check")
    public R<Void> checkEntry(@RequestParam String entry) {
        authService.assertEntryAllowed(LoginContext.currentUserId(), EntryType.of(entry));
        return R.ok();
    }
}
