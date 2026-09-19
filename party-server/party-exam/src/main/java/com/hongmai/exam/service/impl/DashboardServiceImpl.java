package com.hongmai.exam.service.impl;

import com.hongmai.auth.spi.UserRosterProvider;
import com.hongmai.common.enums.PeriodType;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.util.DateUtil;
import com.hongmai.exam.mapper.ExamRecordMapper;
import com.hongmai.exam.service.DashboardService;
import com.hongmai.exam.vo.DashboardVO;
import com.hongmai.learn.service.CreditService;
import com.hongmai.org.service.OrgService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** 成绩分桶的固定顺序与文案，前端不硬编码 */
    private static final Map<String, String> BUCKET_LABELS = new LinkedHashMap<>();

    static {
        BUCKET_LABELS.put("A", "60 分以下");
        BUCKET_LABELS.put("B", "60-69 分");
        BUCKET_LABELS.put("C", "70-79 分");
        BUCKET_LABELS.put("D", "80-89 分");
        BUCKET_LABELS.put("E", "90 分及以上");
    }

    private final OrgService orgService;
    private final UserRosterProvider userRosterProvider;
    private final CreditService creditService;
    private final ExamRecordMapper examRecordMapper;

    @Override
    public DashboardVO dashboard(long operatorId, long orgId, LocalDate from, LocalDate to) {
        LocalDate today = DateUtil.today();
        LocalDate effectiveFrom = from == null ? LocalDate.of(today.getYear(), 1, 1) : from;
        LocalDate effectiveTo = to == null ? today : to;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "统计区间起止顺序颠倒");
        }

        List<Long> scope = resolveScope(operatorId, orgId);
        String periodKey = DateUtil.yearKey(effectiveTo);

        DashboardVO vo = new DashboardVO();
        vo.setPeriodKey(periodKey);
        vo.setFromDate(effectiveFrom.toString());
        vo.setToDate(effectiveTo.toString());

        // 指标 1：在册人数
        long rosterCount = userRosterProvider.countRoster(scope);
        vo.setRosterCount(rosterCount);

        // 指标 2、3、5：来自学习模块的聚合概要
        CreditService.CreditSummary creditSummary = creditService.summary(
                scope, PeriodType.NATURAL_YEAR, periodKey, effectiveFrom, effectiveTo);
        vo.setActiveUsers(creditSummary.activeUsers());
        vo.setLearnerCount(creditSummary.learnerCount());
        vo.setAchievedCount(creditSummary.achievedCount());
        vo.setTotalCredit(creditSummary.totalCredit());
        vo.setTotalMinutes(creditSummary.totalMinutes());
        vo.setActiveRate(percent(creditSummary.activeUsers(), rosterCount));
        // 达标率的分母是「有学时记录的人数」而不是在册人数——口径不同，注释见 DashboardVO
        vo.setAchieveRate(percent(creditSummary.achievedCount(), creditSummary.learnerCount()));

        // 指标 4：答题成绩
        LocalDateTime fromTime = effectiveFrom.atStartOfDay();
        LocalDateTime toTime = effectiveTo.atTime(23, 59, 59);
        Map<String, Object> avgRow = examRecordMapper.statAvgScore(scope, fromTime, toTime);
        vo.setExamAvgScore(avgRow == null || avgRow.get("avgScore") == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal(avgRow.get("avgScore").toString()).setScale(2, RoundingMode.HALF_UP));
        vo.setExamUserCount(avgRow == null || avgRow.get("userCount") == null
                ? 0L : ((Number) avgRow.get("userCount")).longValue());
        vo.setScoreBuckets(buildBuckets(examRecordMapper.statScoreBuckets(scope, fromTime, toTime)));

        return vo;
    }

    /** 分布桶补齐零值：某一段没人时也要返回，否则前端画图会缺柱子。 */
    private List<DashboardVO.ScoreBucket> buildBuckets(List<Map<String, Object>> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object bucket = row.get("bucket");
            Object cnt = row.get("cnt");
            if (bucket != null && cnt != null) {
                counts.put(bucket.toString(), ((Number) cnt).longValue());
            }
        }
        List<DashboardVO.ScoreBucket> result = new ArrayList<>(BUCKET_LABELS.size());
        for (Map.Entry<String, String> entry : BUCKET_LABELS.entrySet()) {
            DashboardVO.ScoreBucket bucket = new DashboardVO.ScoreBucket();
            bucket.setKey(entry.getKey());
            bucket.setLabel(entry.getValue());
            bucket.setCount(counts.getOrDefault(entry.getKey(), 0L));
            result.add(bucket);
        }
        return result;
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    /** 与名册一致的三层语义：null 不限范围、空集返回空、指定组织必须在范围内。 */
    private List<Long> resolveScope(long operatorId, long orgId) {
        List<Long> visible = orgService.resolveVisibleOrgIds(operatorId);
        if (orgId <= 0) {
            return visible;
        }
        var org = orgService.requireEnabled(orgId);
        List<Long> subtree = orgMapperIdsUnderPath(org.getPath());
        if (visible == null) {
            return subtree;
        }
        if (visible.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        List<Long> intersected = subtree.stream().filter(visible::contains).toList();
        if (intersected.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return intersected;
    }

    /** 由 party-org 提供的子树 id 查询；见 OrgService 的可见性方法。 */
    private List<Long> orgMapperIdsUnderPath(String path) {
        return orgService.idsUnderPath(path);
    }
}
