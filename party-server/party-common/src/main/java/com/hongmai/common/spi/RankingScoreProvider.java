package com.hongmai.common.spi;

/**
 * 榜单分值写入能力（跨模块 SPI）。
 *
 * 为什么要这个接口：学时产生在 party-learn，而榜单实现在 party-exam，
 * 且 party-exam 依赖 party-learn（方向不能反过来）。如果让 learn 直接调 exam 就成环了。
 * 因此把「写榜单分值」抽象到 common，由 party-exam 实现，party-learn 按需消费。
 *
 * 这是本项目第三处依赖倒置，前两处是 AuditableHandler 与 OrgLevelProvider。
 *
 * 消费方须用 ObjectProvider 注入并容忍实现缺失——榜单是增值功能，
 * 缺失时学时照常入账，不能因为榜单不可用而让核心链路失败。
 */
public interface RankingScoreProvider {

    /** 累加学时榜单分值。 */
    void increaseCredit(long orgId, long userId, int periodType, String periodKey, double credit);

    /** 取较大值写入答题榜单分值（答题榜记最高分而非累加分）。 */
    void keepMaxExamScore(long orgId, long userId, int periodType, String periodKey, double score);
}
