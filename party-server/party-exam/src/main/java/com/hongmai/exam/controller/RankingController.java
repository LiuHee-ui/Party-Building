package com.hongmai.exam.controller;

import com.hongmai.common.enums.RankingMetric;
import com.hongmai.common.enums.RankingPeriodType;
import com.hongmai.common.util.DateUtil;
import com.hongmai.common.web.LoginContext;
import com.hongmai.common.web.R;
import com.hongmai.exam.service.RankingService;
import com.hongmai.exam.vo.RankingItemVO;
import com.hongmai.org.service.OrgService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ranking")
@RequiredArgsConstructor
public class RankingController {

    private static final int DEFAULT_LIMIT = 20;

    private final RankingService rankingService;
    private final OrgService orgService;

    /**
     * 用户端：本组织榜。
     * 组织取自登录态，不接受客户端传入 orgId —— 否则可以随意查看其他组织的榜单。
     */
    @GetMapping("/my-org")
    public R<List<RankingItemVO>> myOrg(@RequestParam(defaultValue = "learn") String metric,
                                        @RequestParam(defaultValue = "2") int periodType,
                                        @RequestParam(defaultValue = "20") int limit) {
        LoginContext context = LoginContext.get();
        long orgId = context == null ? 0L : context.getPrimaryOrgId();
        String periodKey = DateUtil.rankingPeriodKey(DateUtil.today(), periodType);
        long self = LoginContext.currentUserId();

        List<RankingItemVO> items = rankingService.topOfOrg(orgId, resolveMetric(metric),
                periodType, periodKey, normalizeLimit(limit));
        items.forEach(item -> item.setSelf(item.getUserId() != null && item.getUserId() == self));
        return R.ok(items);
    }

    /**
     * 管理端：跨组织榜，读快照表。
     * 数据范围与名册一致——只能看本组织及下辖。
     */
    @GetMapping("/cross-org")
    public R<List<RankingItemVO>> crossOrg(@RequestParam(defaultValue = "learn") String metric,
                                           @RequestParam(defaultValue = "4") int periodType,
                                           @RequestParam(required = false) Long orgId,
                                           @RequestParam(defaultValue = "50") int limit) {
        long operatorId = LoginContext.currentUserId();
        String periodKey = DateUtil.rankingPeriodKey(DateUtil.today(), periodType);

        List<Long> scope;
        if (orgId == null || orgId <= 0) {
            scope = orgService.resolveVisibleOrgIds(operatorId);
        } else {
            scope = orgService.idsUnderPath(orgService.requireEnabled(orgId).getPath());
        }
        return R.ok(rankingService.topAcrossOrgs(scope, resolveMetric(metric),
                periodType, periodKey, normalizeLimit(limit)));
    }

    /** 我的名次。 */
    @GetMapping("/my-rank")
    public R<Map<String, Object>> myRank(@RequestParam(defaultValue = "learn") String metric,
                                         @RequestParam(defaultValue = "2") int periodType) {
        LoginContext context = LoginContext.get();
        long orgId = context == null ? 0L : context.getPrimaryOrgId();
        String periodKey = DateUtil.rankingPeriodKey(DateUtil.today(), periodType);
        Long rank = rankingService.rankOf(orgId, LoginContext.currentUserId(),
                resolveMetric(metric), periodType, periodKey);
        return R.ok(Map.of("rank", rank == null ? 0L : rank, "periodKey", periodKey));
    }

    /** 可选的周期清单，前端渲染切换器用。 */
    @GetMapping("/periods")
    public R<List<Map<String, Object>>> periods() {
        return R.ok(java.util.Arrays.stream(RankingPeriodType.values())
                .map(type -> Map.<String, Object>of(
                        "periodType", type.getCode(),
                        "label", type.getLabel()))
                .toList());
    }

    private RankingMetric resolveMetric(String metric) {
        return "exam".equalsIgnoreCase(metric) ? RankingMetric.EXAM_SCORE : RankingMetric.LEARN_CREDIT;
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 100);
    }
}
