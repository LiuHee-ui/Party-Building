package com.hongmai.exam.controller;

import com.hongmai.common.web.LoginContext;
import com.hongmai.common.web.R;
import com.hongmai.exam.service.DashboardService;
import com.hongmai.exam.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** 管理端看板五指标。orgId 不传表示取操作员可见范围。 */
    @GetMapping
    public R<DashboardVO> dashboard(
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(dashboardService.dashboard(LoginContext.currentUserId(),
                orgId == null ? 0L : orgId, from, to));
    }
}
