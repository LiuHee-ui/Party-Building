package com.hongmai.boot.config;

import com.hongmai.boot.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器注册。
 * 白名单只放「登录前必须可访问」的接口——登录本身、健康检查、文件静态访问。
 * 其余一律要求登录态，默认拒绝而不是默认放行。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String[] WHITELIST = {
            "/auth/login",
            // 开发免微信登录。生产环境该 Controller 不会注册（@ConditionalOnProperty），
            // 路径虽在白名单里但会直接 404，不构成额外暴露面
            "/dev/auth/login",
            "/actuator/health",
            "/files/**",
            "/error"
    };

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(WHITELIST);
    }
}
