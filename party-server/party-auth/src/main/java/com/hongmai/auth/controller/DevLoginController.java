package com.hongmai.auth.controller;

import com.hongmai.auth.service.AuthService;
import com.hongmai.auth.vo.LoginResult;
import com.hongmai.common.web.R;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发联调用的免微信登录入口。
 *
 * 安全边界：整个 Bean 由 @ConditionalOnProperty 控制，
 * **只有 app.dev-login-enabled=true 时才会注册**。生产配置里不设这项（默认 false），
 * 该 Controller 根本不存在，访问会直接 404 —— 不是靠运行时判断，而是根本没这个 Bean。
 *
 * 为什么需要它：真实小程序拿不到稳定的 openId，没有这一步，
 * 后端的请求链路（登录 → 实名 → 审核 → 学习 → 学时 → 榜单）就无法在本地端到端验证。
 *
 * 启动时打一条醒目日志，避免误开在生产环境还没人发现。
 */
@Slf4j
@RestController
@RequestMapping("/dev/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.dev-login-enabled", havingValue = "true")
public class DevLoginController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<LoginResult> login(@RequestParam @NotBlank String openId) {
        log.warn("★ 使用开发免微信登录 openId={} —— 该入口仅应在开发环境开启", openId);
        return R.ok(authService.loginByOpenId(openId));
    }
}
