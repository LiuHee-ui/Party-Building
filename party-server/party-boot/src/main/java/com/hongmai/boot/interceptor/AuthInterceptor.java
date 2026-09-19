package com.hongmai.boot.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongmai.common.auth.TokenPayload;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.web.LoginContext;
import com.hongmai.common.web.R;
import com.hongmai.org.service.OrgService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 登录态解析拦截器。
 *
 * 职责：解析身份 → **立即解析数据范围** → 注入 LoginContext；请求结束时清理。
 *
 * 为什么数据范围在这里"立即"解析，而不是等标注了 @DataScoped 的方法被调用时才解析：
 * 惰性解析踩过一个真实的坑 —— LoginContext.visibleOrgIds 的默认值是空列表，
 * 而「尚未解析」与「解析结果确为空」都表现为空列表。某个接口忘了标注注解时，
 * 查询会拿到默认空列表并被当成「无可见组织」，**表现为静默返回 0 条**（而不是报错），
 * 排查时很难定位到是权限上下文没初始化。
 *
 * 安全相关的上下文不应该依赖「记得标注注解」这种纪律，所以改成每个已登录请求统一解析一次。
 * 代价是每请求多一到两次组织查询，相对越权风险这笔账是划算的。
 *
 * 操作员身份一律由本拦截器从 Token 解析，controller 不接受客户端传入的操作员 id。
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final OrgService orgService;

    public AuthInterceptor(StringRedisTemplate redis, ObjectMapper objectMapper, OrgService orgService) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.orgService = orgService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response);
            return false;
        }

        String json = redis.opsForValue().get(TokenPayload.redisKey(token));
        if (!StringUtils.hasText(json)) {
            writeUnauthorized(response);
            return false;
        }

        TokenPayload payload = objectMapper.readValue(json, TokenPayload.class);
        LoginContext context = new LoginContext();
        context.setUserId(payload.userId());
        context.setRoles(payload.roles());
        context.setPrimaryOrgId(payload.primaryOrgId());
        context.setLeader(payload.leader());
        LoginContext.set(context);

        // 统一在此解析数据范围。null 表示不限范围（平台管理员），空列表表示确无可见组织，
        // 两者语义不同，业务侧必须区别对待
        try {
            List<Long> visible = orgService.resolveVisibleOrgIds(payload.userId());
            context.setVisibleOrgIds(visible);
            context.setScopeResolved(true);
        } catch (Exception e) {
            // 解析失败必须让请求失败，绝不能放行一个「没有范围」的上下文
            log.error("数据范围解析失败，拒绝请求 userId={}", payload.userId(), e);
            LoginContext.clear();
            writeUnauthorized(response);
            return false;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清理：线程复用会导致下一个请求拿到上一个请求的身份
        LoginContext.clear();
    }

    /** 是否为平台管理员（可见组织为 null 表示不限范围）。 */
    public static boolean isScopeUnlimited(LoginContext context) {
        return context != null && context.getVisibleOrgIds() == null;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        R<Void> body = R.fail(ErrorCode.TOKEN_INVALID);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
