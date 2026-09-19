package com.hongmai.exam.service;

import com.hongmai.common.enums.RankingMetric;
import com.hongmai.exam.vo.RankingItemVO;

import java.util.List;

public interface RankingService {

    /**
     * 榜单分值累加（学时场景）。
     * 用 ZINCRBY 而不是「读出来加再写回」——前者是原子操作，并发安全。
     */
    void increaseScore(long orgId, long userId, RankingMetric metric,
                       int periodType, String periodKey, double delta);

    /**
     * 榜单分值取较大值（答题得分场景）。
     * 答题榜记录的是「周期内最高分」，不是累加分，所以不能累加。
     */
    void keepMaxScore(long orgId, long userId, RankingMetric metric,
                      int periodType, String periodKey, double score);

    /** 本组织个人榜 Top N，按分值倒序。 */
    List<RankingItemVO> topOfOrg(long orgId, RankingMetric metric,
                                 int periodType, String periodKey, int limit);

    /**
     * 某人在本组织榜的名次，从 1 起。不在榜返回 null。
     * ZREVRANK 返回的是 0 起的下标，需要 +1。
     */
    Long rankOf(long orgId, long userId, RankingMetric metric, int periodType, String periodKey);

    /**
     * 管理端跨组织榜：读 t_ranking_snapshot 快照表，不查 Redis。
     * 原因见 RankingKey 的说明——Redis 里个人榜是按组织分片的，跨组织聚合要读 N 个分片，
     * 而管理端本来就是低频访问，读持久化快照更合适。
     */
    List<RankingItemVO> topAcrossOrgs(List<Long> orgIds, RankingMetric metric,
                                      int periodType, String periodKey, int limit);
}
