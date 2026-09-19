package com.hongmai.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端看板。
 *
 * 五项指标及其口径（口径必须写死在文档与代码里，否则不同人算出的数字不一致）：
 *   1. 在册人数   identity_type IN (1,2,3) AND audit_status = 1（党员 + 积极分子）
 *   2. 活跃度     区间内有有效学习分钟数 > 0 的人数 / 在册人数
 *   3. 达标率     有学时记录的人中，累计学时 ≥ 目标学时 的比例
 *   4. 答题成绩   区间内每人最高分的平均分，以及分数段分布
 *   5. 累计学时   区间内累计学时与分钟数
 *
 * 注意 2 与 3 的分母不同：活跃度分母是「在册人数」，达标率分母是「有学时记录的人数」。
 * 混用会让数字看着接近但含义不同，是可解释性问题。
 */
@Data
public class DashboardVO {

    private String periodKey;
    private String fromDate;
    private String toDate;

    // 指标 1：在册人数
    private Long rosterCount;

    // 指标 2：活跃度
    private Long activeUsers;
    private BigDecimal activeRate;

    // 指标 3：达标率
    private Integer learnerCount;
    private Integer achievedCount;
    private BigDecimal achieveRate;

    // 指标 4：答题成绩
    private BigDecimal examAvgScore;
    private Long examUserCount;
    private List<ScoreBucket> scoreBuckets;

    // 指标 5：累计学时
    private BigDecimal totalCredit;
    private Integer totalMinutes;

    /** 成绩分布桶。key 用 A-E，label 给出区间，避免前端硬编码文案。 */
    @Data
    public static class ScoreBucket {
        private String key;
        private String label;
        private Long count;
    }
}
