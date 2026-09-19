package com.hongmai.common.auth;

import java.util.List;
import java.util.Set;

/**
 * 登录态载荷。
 * 写入方：party-auth 的 AuthService（登录成功时写 Redis）
 * 读取方：party-boot 的 AuthInterceptor（每次请求解析）
 * 两端共用本类，避免 key 格式与字段结构各写一份而对不上。
 */
public record TokenPayload(
        long userId,
        Set<Integer> roles,
        long primaryOrgId,
        /**
         * 是否为党组织书记或班子成员。
         * 这是用户属性而不是角色，所以随登录态一起携带，
         * 避免每个需要它的接口都回头查一次用户表。
         */
        boolean leader
) {

    /** 兼容旧调用：不关心书记标识时按 false 处理。 */
    public TokenPayload(long userId, Set<Integer> roles, long primaryOrgId) {
        this(userId, roles, primaryOrgId, false);
    }

    /** Redis 键前缀，完整键为 auth:token:{token}。 */
    public static final String TOKEN_KEY_PREFIX = "auth:token:";

    /** 登录态有效期（秒），7 天固定 TTL，不做滑动续期。 */
    public static final long TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60L;

    public static String redisKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }

    public List<Integer> roleList() {
        return roles == null ? List.of() : List.copyOf(roles);
    }
}
