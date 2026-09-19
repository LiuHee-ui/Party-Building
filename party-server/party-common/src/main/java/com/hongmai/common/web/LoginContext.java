package com.hongmai.common.web;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 当前登录上下文。由 party-boot 的 AuthInterceptor 在请求进入时写入，
 * 可见组织范围由 DataScopeAspect 解析后回填（各业务模块只读，不自行解析范围）。
 */
@Data
public class LoginContext {

    private long userId;
    private Set<Integer> roles = Collections.emptySet();
    private List<Long> visibleOrgIds = Collections.emptyList();
    private long primaryOrgId;

    /** 是否为党组织书记或班子成员，决定默认目标学时（56/年 或 32/年）。 */
    private boolean leader;

    /**
     * 数据范围是否已解析。
     * 不能靠 visibleOrgIds 是否为空来判断——「已解析但无可见组织」与「尚未解析」
     * 都表现为空列表，混淆会导致要么重复查库、要么把空集当成未解析而放行。
     */
    private boolean scopeResolved;

    /** 请求级 ThreadLocal，由拦截器在 afterCompletion 中清理，避免线程复用串号。 */
    private static final ThreadLocal<LoginContext> HOLDER = new ThreadLocal<>();

    public static void set(LoginContext context) {
        HOLDER.set(context);
    }

    public static LoginContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static long currentUserId() {
        LoginContext context = HOLDER.get();
        return context == null ? 0L : context.userId;
    }

    public static List<Long> currentVisibleOrgIds() {
        LoginContext context = HOLDER.get();
        return context == null ? Collections.emptyList() : context.visibleOrgIds;
    }

    public boolean hasRole(int role) {
        return roles != null && roles.contains(role);
    }
}
