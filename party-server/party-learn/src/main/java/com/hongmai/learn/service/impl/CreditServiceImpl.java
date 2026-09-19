package com.hongmai.learn.service.impl;

import com.alibaba.excel.EasyExcel;
import com.hongmai.auth.spi.UserRosterProvider;
import com.hongmai.common.enums.IdentityType;
import com.hongmai.common.enums.PeriodType;
import com.hongmai.common.util.DateUtil;
import com.hongmai.common.spi.RankingScoreProvider;
import com.hongmai.learn.domain.CreditDaily;
import com.hongmai.learn.domain.CreditTargetRule;
import com.hongmai.learn.mapper.CreditDailyMapper;
import com.hongmai.learn.mapper.CreditMapper;
import com.hongmai.learn.service.CreditService;
import com.hongmai.learn.service.CreditTargetService;
import com.hongmai.learn.vo.CreditOverviewVO;
import com.hongmai.learn.vo.OrgLedgerRowVO;
import com.hongmai.org.service.OrgService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private final CreditDailyMapper creditDailyMapper;
    private final CreditMapper creditMapper;
    private final CreditTargetService creditTargetService;
    private final UserRosterProvider userRosterProvider;
    private final OrgService orgService;

    /**
     * 榜单写入能力（可选依赖）。
     * 榜单实现在 party-exam，而 party-exam 依赖 party-learn，方向不能反过来，
     * 所以走 common 里的 SPI。用 ObjectProvider 容忍缺失——榜单是增值功能，
     * 不可用时学时照常入账，不能让它拖垮核心链路。
     */
    private final ObjectProvider<RankingScoreProvider> rankingScoreProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccrualResult accrue(long userId, long orgId, long resourceId, LocalDate statDate, int deltaSec) {
        int deltaMinutes = com.hongmai.learn.domain.CreditMath.toMinutes(deltaSec);

        CreditDaily daily = creditDailyMapper.selectForUpdate(userId, resourceId, statDate);
        if (daily == null && deltaMinutes > 0) {
            daily = tryCreateDaily(userId, orgId, resourceId, statDate);
            if (daily == null) {
                // 竞态：另一个请求刚插入，加锁重读
                daily = creditDailyMapper.selectForUpdate(userId, resourceId, statDate);
            }
        }
        if (daily == null) {
            // 无增量且尚无当日记录时直接返回零，不为「被拒心跳」凭空建行
            return new AccrualResult(0, 0, BigDecimal.ZERO, false);
        }

        int today = daily.getMinutes() == null ? 0 : daily.getMinutes();
        int added = com.hongmai.learn.domain.CreditMath.acceptableMinutes(today, deltaMinutes);
        int newToday = today + added;

        if (added > 0) {
            CreditDaily update = new CreditDaily();
            update.setId(daily.getId());
            update.setMinutes(newToday);
            creditDailyMapper.updateById(update);
            writeLedgers(userId, orgId, resourceId, statDate, daily.getId(), newToday);
            pushToRanking(orgId, userId, statDate, added);
        }

        return new AccrualResult(newToday, added,
                com.hongmai.learn.domain.CreditMath.toCredit(newToday),
                com.hongmai.learn.domain.CreditMath.reachedDailyCap(newToday));
    }

    private CreditDaily tryCreateDaily(long userId, long orgId, long resourceId, LocalDate statDate) {
        CreditDaily entity = new CreditDaily();
        entity.setUserId(userId);
        entity.setOrgId(orgId);
        entity.setResourceId(resourceId);
        entity.setStatDate(statDate);
        entity.setMinutes(0);
        try {
            creditDailyMapper.insert(entity);
            return entity;
        } catch (DuplicateKeyException e) {
            // uk_user_resource_date 兜住了并发，属于预期情况，交给调用方重读
            return null;
        }
    }

    /**
     * 同时写自然年与五年规划期两本账。
     * 两个周期口径都要能独立查询，所以在两个 period_key 下各写一条分录——
     * 靠 uk_credit_source 保证幂等，重复调用只刷新数值。
     */
    private void writeLedgers(long userId, long orgId, long resourceId, LocalDate statDate,
                              long dailyId, int todayMinutes) {
        BigDecimal credit = com.hongmai.learn.domain.CreditMath.toCredit(todayMinutes);

        creditMapper.upsertDailyLedger(userId, orgId, PeriodType.NATURAL_YEAR.getCode(),
                DateUtil.yearKey(statDate), com.hongmai.learn.domain.Credit.SOURCE_LEARN,
                dailyId, todayMinutes, credit, statDate);

        creditMapper.upsertDailyLedger(userId, orgId, PeriodType.FIVE_YEAR_PLAN.getCode(),
                DateUtil.fiveYearPeriodKey(statDate), com.hongmai.learn.domain.Credit.SOURCE_LEARN,
                dailyId, todayMinutes, credit, statDate);
    }

    /**
     * 把本次新增学时推给榜单。
     * 只推年度榜——周/月/季榜由每日快照任务重算，避免每次心跳都算一遍多周期。
     * 榜单不可用时静默跳过，由快照任务兜底。
     *
     * 注意周期编码：这里必须用 RankingPeriodType.YEAR，不能用 PeriodType.NATURAL_YEAR ——
     * 两者都是 1..4 的编码但含义不同（前者是榜单换榜周期，后者是学时统计口径），
     * 混用会把学时写进错误的榜单分片，且不报任何错。
     */
    private void pushToRanking(long orgId, long userId, LocalDate statDate, int addedMinutes) {
        RankingScoreProvider provider = rankingScoreProvider.getIfAvailable();
        if (provider == null) {
            return;
        }
        double deltaCredit = com.hongmai.learn.domain.CreditMath.toCredit(addedMinutes).doubleValue();
        provider.increaseCredit(orgId, userId,
                com.hongmai.common.enums.RankingPeriodType.YEAR.getCode(),
                DateUtil.yearKey(statDate), deltaCredit);
    }

    @Override
    public CreditOverviewVO overview(long userId, boolean leader) {
        LocalDate today = DateUtil.today();
        String yearKey = DateUtil.yearKey(today);
        String fiveYearKey = DateUtil.fiveYearPeriodKey(today);

        BigDecimal yearCredit = nullSafe(creditMapper.sumCredit(userId, yearKey));
        BigDecimal fiveYearCredit = nullSafe(creditMapper.sumCredit(userId, fiveYearKey));

        BigDecimal yearTarget = creditTargetService.resolveTarget(
                userId, leader, PeriodType.NATURAL_YEAR, yearKey);
        BigDecimal fiveYearTarget = creditTargetService.resolveTarget(
                userId, leader, PeriodType.FIVE_YEAR_PLAN, fiveYearKey);

        CreditOverviewVO vo = new CreditOverviewVO();
        vo.setYearKey(yearKey);
        vo.setYearCredit(yearCredit);
        vo.setYearTarget(yearTarget);
        vo.setYearPercent(CreditTargetRule.achievementPercent(yearCredit, yearTarget));
        vo.setYearAchieved(CreditTargetRule.isAchieved(yearCredit, yearTarget));
        vo.setYearTargetCustomized(creditTargetService.isCustomized(
                userId, PeriodType.NATURAL_YEAR, yearKey));

        vo.setFiveYearKey(fiveYearKey);
        vo.setFiveYearCredit(fiveYearCredit);
        vo.setFiveYearTarget(fiveYearTarget);
        vo.setFiveYearPercent(CreditTargetRule.achievementPercent(fiveYearCredit, fiveYearTarget));
        vo.setFiveYearAchieved(CreditTargetRule.isAchieved(fiveYearCredit, fiveYearTarget));

        // 自然年累计分钟：按当年 1 月 1 日到今天的区间统计
        vo.setYearMinutes(creditDailyMapper.sumMinutesOfPeriod(userId,
                LocalDate.of(today.getYear(), 1, 1), today));
        return vo;
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @Override
    public List<OrgLedgerRowVO> orgLedger(List<Long> orgIds, PeriodType periodType, String periodKey) {
        if (orgIds != null && orgIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = creditMapper.statOrgLedger(
                orgIds, periodType.getCode(), periodKey);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object userId = row.get("userId");
            if (userId != null) {
                userIds.add(((Number) userId).longValue());
            }
        }

        // 姓名与身份属 party-auth，组织名属 party-org，都在内存里回填，不在 SQL 里跨模块 JOIN
        Map<Long, UserRosterProvider.RosterRow> rosterMap = new HashMap<>(userIds.size());
        for (UserRosterProvider.RosterRow row : userRosterProvider.findByIds(userIds)) {
            rosterMap.put(row.userId(), row);
        }
        List<Long> orgIdList = rosterMap.values().stream()
                .map(UserRosterProvider.RosterRow::orgId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> orgNames = orgService.namesOf(orgIdList);

        List<OrgLedgerRowVO> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            long userId = ((Number) row.get("userId")).longValue();
            int minutes = ((Number) row.get("totalMinutes")).intValue();
            BigDecimal credit = toBigDecimal(row.get("totalCredit"));

            UserRosterProvider.RosterRow roster = rosterMap.get(userId);
            boolean leader = roster != null && roster.leaderFlag() != null && roster.leaderFlag() == 1;
            BigDecimal target = creditTargetService.resolveTarget(
                    userId, leader, periodType, periodKey);

            OrgLedgerRowVO vo = new OrgLedgerRowVO();
            vo.setUserId(userId);
            vo.setRealName(roster == null ? "未知用户" : roster.realName());
            vo.setEthnicity(roster == null ? null : roster.ethnicity());
            vo.setIdentityTypeLabel(roster == null ? null : identityLabel(roster.identityType()));
            vo.setOrgName(roster == null || roster.orgId() == null ? null : orgNames.get(roster.orgId()));
            vo.setTotalMinutes(minutes);
            vo.setTotalCredit(credit);
            vo.setTargetCredit(target);

            BigDecimal percent = CreditTargetRule.achievementPercent(credit, target);
            vo.setAchievementText(percent.toPlainString() + "%");
            vo.setAchievedText(CreditTargetRule.isAchieved(credit, target) ? "已达标" : "未达标");
            result.add(vo);
        }
        return result;
    }

    @Override
    public byte[] exportOrgLedger(List<Long> orgIds, PeriodType periodType, String periodKey) {
        List<OrgLedgerRowVO> rows = orgLedger(orgIds, periodType, periodKey);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, OrgLedgerRowVO.class)
                    .sheet("学时台账")
                    .doWrite(rows);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("台账导出失败", e);
            throw new com.hongmai.common.exception.BizException(
                    com.hongmai.common.exception.ErrorCode.SYSTEM_ERROR, "台账导出失败");
        }
    }

    @Override
    public CreditSummary summary(List<Long> orgIds, PeriodType periodType, String periodKey,
                                 LocalDate from, LocalDate to) {
        if (orgIds != null && orgIds.isEmpty()) {
            return new CreditSummary(0L, 0, 0, BigDecimal.ZERO, 0);
        }

        long activeUsers = creditDailyMapper.countActiveUsers(orgIds, from, to);

        List<Map<String, Object>> rows = creditMapper.statOrgLedger(
                orgIds, periodType.getCode(), periodKey);
        if (rows.isEmpty()) {
            return new CreditSummary(activeUsers, 0, 0, BigDecimal.ZERO, 0);
        }

        List<Long> userIds = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object userId = row.get("userId");
            if (userId != null) {
                userIds.add(((Number) userId).longValue());
            }
        }
        Map<Long, UserRosterProvider.RosterRow> rosterMap = new HashMap<>(userIds.size());
        for (UserRosterProvider.RosterRow row : userRosterProvider.findByIds(userIds)) {
            rosterMap.put(row.userId(), row);
        }

        int achieved = 0;
        BigDecimal totalCredit = BigDecimal.ZERO;
        int totalMinutes = 0;
        for (Map<String, Object> row : rows) {
            long userId = ((Number) row.get("userId")).longValue();
            BigDecimal credit = toBigDecimal(row.get("totalCredit"));
            totalCredit = totalCredit.add(credit);
            totalMinutes += ((Number) row.get("totalMinutes")).intValue();

            UserRosterProvider.RosterRow roster = rosterMap.get(userId);
            boolean leader = roster != null && roster.leaderFlag() != null && roster.leaderFlag() == 1;
            BigDecimal target = creditTargetService.resolveTarget(
                    userId, leader, periodType, periodKey);
            if (CreditTargetRule.isAchieved(credit, target)) {
                achieved++;
            }
        }
        return new CreditSummary(activeUsers, rows.size(), achieved, totalCredit, totalMinutes);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    @Override
    public List<UserCreditRow> userCreditsInRange(List<Long> orgIds, LocalDate from, LocalDate to) {
        if (orgIds != null && orgIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = creditMapper.statUserCreditInRange(
                orgIds, PeriodType.NATURAL_YEAR.getCode(), from, to);
        List<UserCreditRow> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object userId = row.get("userId");
            Object orgId = row.get("orgId");
            if (userId == null) {
                continue;
            }
            result.add(new UserCreditRow(
                    ((Number) userId).longValue(),
                    orgId == null ? 0L : ((Number) orgId).longValue(),
                    toBigDecimal(row.get("credit"))));
        }
        return result;
    }

    private String identityLabel(Integer identityType) {
        if (identityType == null) {
            return null;
        }
        try {
            return IdentityType.of(identityType).getLabel();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
