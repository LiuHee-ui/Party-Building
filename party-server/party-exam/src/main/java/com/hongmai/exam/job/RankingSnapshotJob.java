package com.hongmai.exam.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongmai.common.enums.RankingMetric;
import com.hongmai.common.enums.RankingScopeType;
import com.hongmai.common.util.DateUtil;
import com.hongmai.exam.domain.PeriodRange;
import com.hongmai.exam.domain.RankingKey;
import com.hongmai.exam.domain.RankingSnapshot;
import com.hongmai.exam.mapper.ExamRecordMapper;
import com.hongmai.exam.mapper.RankingSnapshotMapper;
import com.hongmai.learn.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 排行榜快照任务。
 *
 * 每日 02:00 执行，对「周/月/季/年」四个周期分别做两件事：
 *   1. 从数据库重算每人在其所属组织分片上的分值，写回 Redis ZSET
 *   2. 落 t_ranking_snapshot 快照，供管理端跨组织查询
 *
 * 为什么要有这个任务，而不只依赖实时写入：
 *   - 实时写入（RankingScoreProvider）保证用户端榜单即时更新，但它可能因 Redis 抖动丢失
 *   - 快照是**重算**而不是增量，能自动修复丢失与错乱
 *   - 管理端要跨组织看榜，Redis 是按组织分片的，跨组织聚合不适合查 Redis
 *
 * 幂等：先删同 (scope, periodType, periodKey, snapshotDate) 的旧快照再插入，
 * 任务重跑或补跑不会产生重复名次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingSnapshotJob {

    private final CreditService creditService;
    private final ExamRecordMapper examRecordMapper;
    private final RankingSnapshotMapper snapshotMapper;
    private final StringRedisTemplate redis;

    /** 每日 02:00 执行。此时在线用户最少，重算对接口影响最小。 */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledSnapshot() {
        try {
            int written = snapshotAll(DateUtil.today());
            log.info("排行榜快照完成，共写入 {} 条", written);
        } catch (Exception e) {
            // 快照失败不影响次日业务，但要能被监控发现
            log.error("排行榜快照任务失败", e);
        }
    }

    /** 供手工补跑与测试调用。返回写入的快照条数。 */
    @Transactional(rollbackFor = Exception.class)
    public int snapshotAll(LocalDate date) {
        int total = 0;
        for (PeriodRange range : PeriodRange.allOf(date)) {
            total += snapshotMetric(RankingMetric.LEARN_CREDIT, range, date,
                    loadCredits(range.from(), range.to()));
            total += snapshotMetric(RankingMetric.EXAM_SCORE, range, date,
                    loadExamScores(range));
        }
        return total;
    }

    /** 榜单输入行。orgId 直接来自来源数据，不做额外的用户→组织映射查询。 */
    private record ScoreRow(long userId, long orgId, BigDecimal score) {
    }

    /** 学时来源：从 t_credit 按 occurred_on 区间聚合，不查 Redis。 */
    private List<ScoreRow> loadCredits(LocalDate from, LocalDate to) {
        List<ScoreRow> rows = new ArrayList<>();
        for (CreditService.UserCreditRow row : creditService.userCreditsInRange(null, from, to)) {
            rows.add(new ScoreRow(row.userId(), row.orgId(), row.credit()));
        }
        return rows;
    }

    /** 答题来源：区间内每人最高分。org_id 由 t_exam_record 自带，无需另查。 */
    private List<ScoreRow> loadExamScores(PeriodRange range) {
        List<ScoreRow> rows = new ArrayList<>();
        List<Map<String, Object>> raw = examRecordMapper.statBestScorePerUser(
                null, range.from().atStartOfDay(), range.to().atTime(23, 59, 59));
        for (Map<String, Object> row : raw) {
            Object userId = row.get("userId");
            Object orgId = row.get("orgId");
            Object score = row.get("bestScore");
            if (userId == null || score == null) {
                continue;
            }
            rows.add(new ScoreRow(((Number) userId).longValue(),
                    orgId == null ? 0L : ((Number) orgId).longValue(),
                    new BigDecimal(score.toString())));
        }
        return rows;
    }

    /** 对单个指标 + 单个周期生成榜单。 */
    private int snapshotMetric(RankingMetric metric, PeriodRange range, LocalDate date,
                               List<ScoreRow> scores) {
        // 按组织分片，片内按分值倒序
        Map<Long, List<ScoreRow>> grouped = new LinkedHashMap<>();
        for (ScoreRow row : scores) {
            grouped.computeIfAbsent(row.orgId(), k -> new ArrayList<>()).add(row);
        }

        // 先清掉本次要写的快照，保证任务重跑幂等
        snapshotMapper.delete(new LambdaQueryWrapper<RankingSnapshot>()
                .eq(RankingSnapshot::getScopeType, RankingScopeType.USER.getCode())
                .eq(RankingSnapshot::getPeriodType, range.periodType())
                .eq(RankingSnapshot::getPeriodKey, range.periodKey())
                .eq(RankingSnapshot::getSnapshotDate, date));

        int written = 0;
        for (Map.Entry<Long, List<ScoreRow>> shard : grouped.entrySet()) {
            long orgId = shard.getKey();
            List<ScoreRow> items = new ArrayList<>(shard.getValue());
            // 同分按 userId 升序，保证名次稳定可复现
            items.sort(Comparator.comparing(ScoreRow::score).reversed()
                    .thenComparing(ScoreRow::userId));

            String key = RankingKey.forUser(metric, range.periodType(), range.periodKey(), orgId);
            redis.delete(key);

            int rank = 1;
            for (ScoreRow item : items) {
                redis.opsForZSet().add(key, String.valueOf(item.userId()), item.score().doubleValue());

                RankingSnapshot snapshot = new RankingSnapshot();
                snapshot.setScopeType(RankingScopeType.USER.getCode());
                snapshot.setPeriodType(range.periodType());
                snapshot.setPeriodKey(range.periodKey());
                snapshot.setOrgId(orgId);
                snapshot.setUserId(item.userId());
                snapshot.setScore(item.score());
                snapshot.setRankNo(rank++);
                snapshot.setSnapshotDate(date);
                snapshotMapper.insert(snapshot);
                written++;
            }
        }
        log.debug("榜单快照 metric={} periodKey={} 组织分片数={} 条目数={}",
                metric, range.periodKey(), grouped.size(), written);
        return written;
    }
}
