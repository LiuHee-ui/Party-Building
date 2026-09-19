package com.hongmai.exam.service.impl;

import com.hongmai.auth.spi.UserRosterProvider;
import com.hongmai.common.enums.RankingMetric;
import com.hongmai.common.enums.RankingScopeType;
import com.hongmai.common.util.DateUtil;
import com.hongmai.exam.domain.RankingKey;
import com.hongmai.exam.domain.RankingSnapshot;
import com.hongmai.exam.mapper.RankingSnapshotMapper;
import com.hongmai.exam.service.RankingService;
import com.hongmai.exam.vo.RankingItemVO;
import com.hongmai.org.service.OrgService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {

    /** 榜单保留时长：周期结束后再多留 30 天，便于回溯查看 */
    private static final Duration KEY_TTL = Duration.ofDays(400);

    private final StringRedisTemplate redis;
    private final RankingSnapshotMapper snapshotMapper;
    private final UserRosterProvider userRosterProvider;
    private final OrgService orgService;

    @Override
    public void increaseScore(long orgId, long userId, RankingMetric metric,
                              int periodType, String periodKey, double delta) {
        if (delta == 0) {
            return;
        }
        String key = RankingKey.forUser(metric, periodType, periodKey, orgId);
        redis.opsForZSet().incrementScore(key, String.valueOf(userId), delta);
        ensureTtl(key);
    }

    @Override
    public void keepMaxScore(long orgId, long userId, RankingMetric metric,
                             int periodType, String periodKey, double score) {
        String key = RankingKey.forUser(metric, periodType, periodKey, orgId);
        String member = String.valueOf(userId);

        // 答题榜是「周期内最高分」而非累加，所以要先比再写。
        // 这里不是原子操作，但同一用户同一试卷受 uk_user_paper 保护，
        // 且重复提交会直接返回 4002，所以不存在并发覆盖。
        Double current = redis.opsForZSet().score(key, member);
        if (current == null || score > current) {
            redis.opsForZSet().add(key, member, score);
            ensureTtl(key);
        }
    }

    @Override
    public List<RankingItemVO> topOfOrg(long orgId, RankingMetric metric,
                                        int periodType, String periodKey, int limit) {
        String key = RankingKey.forUser(metric, periodType, periodKey, orgId);
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().reverseRangeWithScores(key, 0, Math.max(0, limit - 1L));
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() != null) {
                userIds.add(Long.parseLong(tuple.getValue()));
            }
        }
        Map<Long, String> names = resolveNames(userIds);

        List<RankingItemVO> result = new ArrayList<>(tuples.size());
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() == null) {
                continue;
            }
            long userId = Long.parseLong(tuple.getValue());
            RankingItemVO vo = new RankingItemVO();
            vo.setUserId(userId);
            vo.setOrgId(orgId);
            vo.setDisplayName(names.getOrDefault(userId, "未知用户"));
            vo.setScore(tuple.getScore() == null ? 0D : tuple.getScore());
            vo.setRankNo(rank++);
            result.add(vo);
        }
        return result;
    }

    @Override
    public Long rankOf(long orgId, long userId, RankingMetric metric,
                       int periodType, String periodKey) {
        String key = RankingKey.forUser(metric, periodType, periodKey, orgId);
        Long zeroBased = redis.opsForZSet().reverseRank(key, String.valueOf(userId));
        // ZREVRANK 是 0 起下标，对外统一从 1 起
        return zeroBased == null ? null : zeroBased + 1;
    }

    @Override
    public List<RankingItemVO> topAcrossOrgs(List<Long> orgIds, RankingMetric metric,
                                             int periodType, String periodKey, int limit) {
        if (orgIds != null && orgIds.isEmpty()) {
            return List.of();
        }

        // 取最新快照日再读全量，避免读到任务执行中途的「半份」数据
        LocalDate snapshotDate = snapshotMapper.selectLatestSnapshotDate(
                RankingScopeType.USER.getCode(), periodType, periodKey);
        if (snapshotDate == null) {
            log.info("榜单快照尚未生成 periodKey={} periodType={}", periodKey, periodType);
            return List.of();
        }

        List<RankingSnapshot> rows = snapshotMapper.selectBySnapshotDate(
                RankingScopeType.USER.getCode(), periodType, periodKey, snapshotDate,
                orgIds, limit);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = rows.stream().map(RankingSnapshot::getUserId).toList();
        Map<Long, String> names = resolveNames(userIds);
        List<Long> orgIdList = rows.stream().map(RankingSnapshot::getOrgId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> orgNames = orgService.namesOf(orgIdList);

        List<RankingItemVO> result = new ArrayList<>(rows.size());
        for (RankingSnapshot row : rows) {
            RankingItemVO vo = new RankingItemVO();
            vo.setUserId(row.getUserId());
            vo.setOrgId(row.getOrgId());
            vo.setOrgName(row.getOrgId() == null ? null : orgNames.get(row.getOrgId()));
            vo.setDisplayName(names.getOrDefault(row.getUserId(), "未知用户"));
            vo.setScore(row.getScore() == null ? 0D : row.getScore().doubleValue());
            vo.setRankNo(row.getRankNo());
            result.add(vo);
        }
        return result;
    }

    /** 姓名属 party-auth，通过 SPI 回填，不在 Redis 层存姓名（避免改名后榜单不同步）。 */
    private Map<Long, String> resolveNames(List<Long> userIds) {
        Map<Long, String> names = new HashMap<>(userIds.size());
        for (UserRosterProvider.RosterRow row : userRosterProvider.findByIds(userIds)) {
            names.put(row.userId(), row.realName());
        }
        return names;
    }

    /** 榜单键设 TTL，避免历史周期数据永久堆积占内存。 */
    private void ensureTtl(String key) {
        Long ttl = redis.getExpire(key);
        if (ttl == null || ttl < 0) {
            redis.expire(key, KEY_TTL);
        }
    }
}
