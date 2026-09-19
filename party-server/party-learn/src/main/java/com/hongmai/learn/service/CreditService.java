package com.hongmai.learn.service;

import com.hongmai.learn.vo.CreditOverviewVO;
import com.hongmai.learn.vo.OrgLedgerRowVO;
import com.hongmai.common.enums.PeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CreditService {

    /**
     * 把一段有效学习时长计入某日某资源。
     *
     * 单条资源单日封顶 120 分钟（spec F8），超出部分直接丢弃而不是报错——
     * 用户继续看是他的自由，只是不再产生学时。
     *
     * @param deltaSec 本次新增的有效学习秒数
     */
    AccrualResult accrue(long userId, long orgId, long resourceId, LocalDate statDate, int deltaSec);

    /**
     * 某用户的学时概览。
     * 同时返回自然年与五年期的进度——两个口径的达标判定不能混用。
     */
    CreditOverviewVO overview(long userId, boolean leader);

    /** 组织维度的学时台账。orgIds 为 null 表示不限范围，空列表表示无可见组织。 */
    List<OrgLedgerRowVO> orgLedger(List<Long> orgIds, PeriodType periodType, String periodKey);

    /** 导出台账为 xlsx 字节流。导出会读取姓名等字段，调用方须写敏感访问日志。 */
    byte[] exportOrgLedger(List<Long> orgIds, PeriodType periodType, String periodKey);

    /**
     * 聚合概要，供管理端看板使用。
     * 比 orgLedger 轻量：不回填姓名与组织名，只做统计。
     */
    CreditSummary summary(List<Long> orgIds, PeriodType periodType, String periodKey,
                          LocalDate from, LocalDate to);

    /**
     * 按日期区间取每人的学时，供排行榜快照任务重算榜单使用。
     * 返回值为纯数据行，不含姓名——姓名由调用方按需回填。
     */
    List<UserCreditRow> userCreditsInRange(List<Long> orgIds, LocalDate from, LocalDate to);

    /** 折算结果。 */
    record AccrualResult(int todayMinutes, int addedMinutes,
                         BigDecimal todayCredit, boolean capReached) {
    }

    /**
     * 聚合概要。
     *
     * @param activeUsers   区间内有有效学习的人数
     * @param learnerCount  有学时记录的人数（分母口径与达标判定一致）
     * @param achievedCount 达标人数
     * @param totalCredit   累计学时
     * @param totalMinutes  累计分钟
     */
    record CreditSummary(long activeUsers, int learnerCount, int achievedCount,
                         BigDecimal totalCredit, int totalMinutes) {
    }

    /** 区间内单人学时，用于排行榜重算。 */
    record UserCreditRow(long userId, long orgId, BigDecimal credit) {
    }
}
