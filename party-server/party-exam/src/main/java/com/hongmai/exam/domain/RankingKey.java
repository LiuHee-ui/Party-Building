package com.hongmai.exam.domain;

import com.hongmai.common.enums.RankingMetric;
import com.hongmai.common.enums.RankingScopeType;

/**
 * 排行榜 Redis 键构造（纯函数）。
 *
 * 分片策略：**个人榜按组织分片**。
 *   rank:1:learn:2:2026-09:3    → 组织 3 的本月学时个人榜
 *
 * 为什么按组织分片而不是一张全国总榜：
 *   1. 用户端只会看「本组织榜单」，分片后每次查询只读自己的分片，天然隔离不会越权
 *   2. 单分片大小可控（一个党支部几十到几百人），ZSET 操作始终是 O(log N) 的小常数
 *   3. 管理端要跨组织看，走 t_ranking_snapshot 快照表，不查 Redis
 *
 * 键里带 periodKey 而不是靠 TTL 过期：过期时间不精确会让「本月榜」在某天突然消失，
 * 显式带周期标识更可控。
 */
public final class RankingKey {

    private static final String PREFIX = "rank";

    private RankingKey() {
    }

    /**
     * 个人榜键。
     *
     * @param orgId 所属组织（党支部），0 表示未归属组织
     */
    public static String forUser(RankingMetric metric, int periodType, String periodKey, long orgId) {
        return String.join(":",
                PREFIX,
                String.valueOf(RankingScopeType.USER.getCode()),
                metric.getKeyPrefix(),
                String.valueOf(periodType),
                periodKey,
                String.valueOf(orgId));
    }

    /** 组织榜键。同一周期同一指标下所有组织在同一个 ZSET 里。 */
    public static String forOrg(RankingMetric metric, int periodType, String periodKey) {
        return String.join(":",
                PREFIX,
                String.valueOf(RankingScopeType.ORG.getCode()),
                metric.getKeyPrefix(),
                String.valueOf(periodType),
                periodKey);
    }

    /** 键前缀，供运维按周期批量清理。 */
    public static String periodPrefix(RankingMetric metric, int periodType, String periodKey) {
        return String.join(":", PREFIX, "*", metric.getKeyPrefix(), String.valueOf(periodType), periodKey) ;
    }

    /** 供测试断言与排障。 */
    public static String prefix() {
        return PREFIX;
    }
}
