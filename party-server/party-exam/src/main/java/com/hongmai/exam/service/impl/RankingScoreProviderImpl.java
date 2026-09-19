package com.hongmai.exam.service.impl;

import com.hongmai.common.enums.RankingMetric;
import com.hongmai.common.spi.RankingScoreProvider;
import com.hongmai.exam.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 榜单写入能力的实现（接口定义在 party-common）。
 *
 * 谨慎处理异常：榜单是增值功能，写榜单失败**不应该让学时入账回滚**。
 * 因此这里吞掉异常只记日志，由每日快照任务兜底重算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingScoreProviderImpl implements RankingScoreProvider {

    private final RankingService rankingService;

    @Override
    public void increaseCredit(long orgId, long userId, int periodType, String periodKey, double credit) {
        try {
            rankingService.increaseScore(orgId, userId, RankingMetric.LEARN_CREDIT,
                    periodType, periodKey, credit);
        } catch (Exception e) {
            log.error("学时榜单写入失败，将由快照任务兜底 orgId={} userId={}", orgId, userId, e);
        }
    }

    @Override
    public void keepMaxExamScore(long orgId, long userId, int periodType, String periodKey, double score) {
        try {
            rankingService.keepMaxScore(orgId, userId, RankingMetric.EXAM_SCORE,
                    periodType, periodKey, score);
        } catch (Exception e) {
            log.error("答题榜单写入失败，将由快照任务兜底 orgId={} userId={}", orgId, userId, e);
        }
    }
}
