package com.hongmai.exam.service.impl;

import com.hongmai.auth.spi.UserRosterProvider;
import com.hongmai.common.enums.RankingMetric;
import com.hongmai.common.enums.RankingScopeType;
import com.hongmai.exam.domain.RankingSnapshot;
import com.hongmai.exam.mapper.RankingSnapshotMapper;
import com.hongmai.exam.vo.RankingItemVO;
import com.hongmai.org.service.OrgService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 排行榜服务测试。
 * 重点：ZSET 的 0 起下标要转成 1 起名次、跨组织榜走快照表、名次缺失要返回 null 而不是 0。
 */
class RankingServiceImplTest {

    private static final int MONTH = 2;
    private static final String PERIOD_KEY = "2026-09";

    private StringRedisTemplate redis;
    private ZSetOperations<String, String> zset;
    private RankingSnapshotMapper snapshotMapper;
    private UserRosterProvider userRosterProvider;
    private OrgService orgService;
    private RankingServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        zset = mock(ZSetOperations.class);
        snapshotMapper = mock(RankingSnapshotMapper.class);
        userRosterProvider = mock(UserRosterProvider.class);
        orgService = mock(OrgService.class);

        when(redis.opsForZSet()).thenReturn(zset);
        service = new RankingServiceImpl(redis, snapshotMapper, userRosterProvider, orgService);
    }

    private ZSetOperations.TypedTuple<String> tuple(String member, double score) {
        return new ZSetOperations.TypedTuple<String>() {
            @Override
            public String getValue() {
                return member;
            }

            @Override
            public Double getScore() {
                return score;
            }

            @Override
            public int compareTo(ZSetOperations.TypedTuple<String> o) {
                return Double.compare(score, o.getScore() == null ? 0 : o.getScore());
            }
        };
    }

    // ---------- 组织内榜单 ----------

    @Test
    @DisplayName("按分值倒序返回，名次从 1 起")
    void topOfOrgRanksFromOne() {
        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(tuple("101", 12.5));
        tuples.add(tuple("102", 8.0));
        when(zset.reverseRangeWithScores("rank:1:learn:2:2026-09:3", 0, 4)).thenReturn(tuples);
        when(userRosterProvider.findByIds(List.of(101L, 102L))).thenReturn(List.of(
                new UserRosterProvider.RosterRow(101L, "张三", "8000", 3L, 1, "藏族", 0),
                new UserRosterProvider.RosterRow(102L, "李四", "8001", 3L, 2, "彝族", 0)));

        List<RankingItemVO> items = service.topOfOrg(3L, RankingMetric.LEARN_CREDIT, MONTH, PERIOD_KEY, 5);

        assertEquals(2, items.size());
        assertEquals(1, items.get(0).getRankNo());
        assertEquals("张三", items.get(0).getDisplayName());
        assertEquals(12.5, items.get(0).getScore());
        assertEquals(2, items.get(1).getRankNo());
    }

    @Test
    @DisplayName("榜单为空时返回空列表，不抛异常")
    void topOfOrgEmptyReturnsEmpty() {
        when(zset.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(Set.of());

        assertTrue(service.topOfOrg(3L, RankingMetric.LEARN_CREDIT, MONTH, PERIOD_KEY, 5).isEmpty());
        verify(userRosterProvider, never()).findByIds(any());
    }

    @Test
    @DisplayName("名次：ZREVRANK 是 0 起，对外要 +1")
    void rankOfIsOneBased() {
        when(zset.reverseRank("rank:1:learn:2:2026-09:3", "101")).thenReturn(0L);

        assertEquals(1L, service.rankOf(3L, 101L, RankingMetric.LEARN_CREDIT, MONTH, PERIOD_KEY));
    }

    @Test
    @DisplayName("不在榜时返回 null，而不是 0 —— 0 会被误认为「第 0 名」")
    void rankOfAbsentReturnsNull() {
        when(zset.reverseRank(anyString(), anyString())).thenReturn(null);

        assertNull(service.rankOf(3L, 999L, RankingMetric.LEARN_CREDIT, MONTH, PERIOD_KEY));
    }

    // ---------- 答题榜取较大值 ----------

    @Test
    @DisplayName("答题榜：新分数更高时写入")
    void keepMaxScoreWritesWhenHigher() {
        when(zset.score(anyString(), eq("101"))).thenReturn(70.0);
        when(redis.getExpire(anyString())).thenReturn(-1L);

        service.keepMaxScore(3L, 101L, RankingMetric.EXAM_SCORE, MONTH, PERIOD_KEY, 85.0);

        verify(zset).add("rank:1:exam:2:2026-09:3", "101", 85.0);
    }

    @Test
    @DisplayName("答题榜：新分数更低时不覆盖，保留历史最高分")
    void keepMaxScoreKeepsExistingHigherScore() {
        when(zset.score(anyString(), eq("101"))).thenReturn(90.0);

        service.keepMaxScore(3L, 101L, RankingMetric.EXAM_SCORE, MONTH, PERIOD_KEY, 60.0);

        verify(zset, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("答题榜：首次提交（榜上无此人）时写入")
    void keepMaxScoreWritesFirstTime() {
        when(zset.score(anyString(), eq("101"))).thenReturn(null);
        when(redis.getExpire(anyString())).thenReturn(-1L);

        service.keepMaxScore(3L, 101L, RankingMetric.EXAM_SCORE, MONTH, PERIOD_KEY, 55.0);

        verify(zset).add("rank:1:exam:2:2026-09:3", "101", 55.0);
    }

    // ---------- 跨组织榜 ----------

    @Test
    @DisplayName("跨组织榜走快照表，不查 Redis")
    void crossOrgReadsSnapshotTable() {
        LocalDate snapshotDate = LocalDate.of(2026, 9, 19);
        when(snapshotMapper.selectLatestSnapshotDate(RankingScopeType.USER.getCode(), MONTH, PERIOD_KEY))
                .thenReturn(snapshotDate);
        RankingSnapshot row = new RankingSnapshot();
        row.setUserId(101L);
        row.setOrgId(3L);
        row.setScore(new BigDecimal("12.5"));
        row.setRankNo(1);
        when(snapshotMapper.selectBySnapshotDate(RankingScopeType.USER.getCode(), MONTH, PERIOD_KEY,
                snapshotDate, List.of(3L), 10)).thenReturn(List.of(row));
        when(userRosterProvider.findByIds(List.of(101L))).thenReturn(List.of(
                new UserRosterProvider.RosterRow(101L, "张三", "8000", 3L, 1, "藏族", 0)));
        when(orgService.namesOf(List.of(3L))).thenReturn(Map.of(3L, "第一村党支部"));

        List<RankingItemVO> items = service.topAcrossOrgs(List.of(3L),
                RankingMetric.LEARN_CREDIT, MONTH, PERIOD_KEY, 10);

        assertEquals(1, items.size());
        assertEquals("第一村党支部", items.get(0).getOrgName());
        assertEquals(1, items.get(0).getRankNo());
        verify(redis, never()).opsForZSet();
    }

    @Test
    @DisplayName("快照尚未生成时返回空列表，不报错")
    void crossOrgWithoutSnapshotReturnsEmpty() {
        when(snapshotMapper.selectLatestSnapshotDate(anyInt(), eq(MONTH), eq(PERIOD_KEY))).thenReturn(null);

        assertTrue(service.topAcrossOrgs(List.of(3L), RankingMetric.LEARN_CREDIT,
                MONTH, PERIOD_KEY, 10).isEmpty());
    }

    @Test
    @DisplayName("可见组织为空时直接返回空，不查库（越权防护）")
    void crossOrgEmptyScopeReturnsEmpty() {
        assertTrue(service.topAcrossOrgs(List.of(), RankingMetric.LEARN_CREDIT,
                MONTH, PERIOD_KEY, 10).isEmpty());
        verify(snapshotMapper, never()).selectLatestSnapshotDate(anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("平台管理员（orgIds=null）不限范围")
    void crossOrgUnlimitedScope() {
        when(snapshotMapper.selectLatestSnapshotDate(anyInt(), anyInt(), anyString())).thenReturn(null);

        service.topAcrossOrgs(null, RankingMetric.LEARN_CREDIT, MONTH, PERIOD_KEY, 10);

        verify(snapshotMapper).selectLatestSnapshotDate(RankingScopeType.USER.getCode(), MONTH, PERIOD_KEY);
    }
}
