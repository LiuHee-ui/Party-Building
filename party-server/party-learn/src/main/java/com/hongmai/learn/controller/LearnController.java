package com.hongmai.learn.controller;

import com.hongmai.common.enums.PeriodType;
import com.hongmai.common.web.LoginContext;
import com.hongmai.common.web.R;
import com.hongmai.learn.dto.HeartbeatCmd;
import com.hongmai.learn.service.CreditService;
import com.hongmai.learn.service.LearnService;
import com.hongmai.learn.vo.CreditOverviewVO;
import com.hongmai.learn.vo.ReportResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 学习与学时接口。用户身份取自 LoginContext，不接受客户端传入 userId。 */
@RestController
@RequestMapping("/learn")
@RequiredArgsConstructor
public class LearnController {

    private final LearnService learnService;
    private final CreditService creditService;

    /**
     * 上报学习心跳。客户端每 15 秒调用一次。
     * 返回值里带当日学时与会话累计，客户端不需要自己算。
     */
    @PostMapping("/heartbeat")
    public R<ReportResultVO> heartbeat(@RequestBody @Valid HeartbeatCmd cmd) {
        return R.ok(learnService.reportHeartbeat(LoginContext.currentUserId(), cmd));
    }

    /**
     * 我的学时概览。自然年与五年期并列返回。
     * 书记标识取自登录态（登录时从用户表写入 Token），不接受客户端传入。
     */
    @GetMapping("/credit/overview")
    public R<CreditOverviewVO> overview() {
        LoginContext context = LoginContext.get();
        long userId = LoginContext.currentUserId();
        boolean leader = context != null && context.isLeader();
        return R.ok(creditService.overview(userId, leader));
    }

    /** 统计周期枚举，供前端渲染切换器。 */
    @GetMapping("/periods")
    public R<java.util.List<java.util.Map<String, Object>>> periods() {
        return R.ok(java.util.Arrays.stream(PeriodType.values())
                .map(p -> java.util.Map.<String, Object>of("code", p.getCode(), "label", p.getLabel()))
                .toList());
    }
}
