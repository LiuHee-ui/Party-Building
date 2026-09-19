package com.hongmai.common.util;

import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;

/**
 * 组织祖先路径工具。
 * 约定：path 形如 /1/12/35/，必须以 /{自身id}/ 结尾。
 * 数据范围查询一律走 path 前缀匹配，避免递归查子组织。
 */
public final class OrgPathUtil {

    private static final String SEP = "/";

    private OrgPathUtil() {
    }

    /** 由上级 path 与自身 id 生成 path。 */
    public static String buildPath(String parentPath, long orgId) {
        if (orgId <= 0) {
            throw new BizException(ErrorCode.ORG_NOT_FOUND, "组织 id 非法");
        }
        String base = normalize(parentPath);
        return base + orgId + SEP;
    }

    /** 归一化：补首尾斜杠，去掉重复斜杠；空值视为根路径。 */
    public static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return SEP;
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith(SEP)) {
            trimmed = SEP + trimmed;
        }
        if (!trimmed.endsWith(SEP)) {
            trimmed = trimmed + SEP;
        }
        return trimmed.replaceAll("/{2,}", SEP);
    }

    /** 判断 candidate 是否落在 ancestorPath 的子树内（含自身）。 */
    public static boolean isDescendantOf(String candidatePath, String ancestorPath) {
        if (candidatePath == null || ancestorPath == null) {
            return false;
        }
        return normalize(candidatePath).startsWith(normalize(ancestorPath));
    }

    /** 取 path 上所有祖先组织 id（含自身）。 */
    public static java.util.List<Long> extractIds(String path) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        String normalized = normalize(path);
        for (String segment : normalized.split(SEP)) {
            if (!segment.isBlank()) {
                ids.add(Long.parseLong(segment));
            }
        }
        return ids;
    }

    /** 取直属上级组织 id，根节点返回 0。 */
    public static long parentIdOf(String path) {
        java.util.List<Long> ids = extractIds(path);
        return ids.size() < 2 ? 0L : ids.get(ids.size() - 2);
    }
}
