package com.hongmai.learn.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 学时概览。自然年与五年期并列返回——两个口径的达标判定不能混用。 */
@Data
public class CreditOverviewVO {

    /** 自然年 key，如 2026 */
    private String yearKey;

    private BigDecimal yearCredit;

    private BigDecimal yearTarget;

    private BigDecimal yearPercent;

    private Boolean yearAchieved;

    /** 五年规划期 key，如 2021-2025 */
    private String fiveYearKey;

    private BigDecimal fiveYearCredit;

    private BigDecimal fiveYearTarget;

    private BigDecimal fiveYearPercent;

    private Boolean fiveYearAchieved;

    /** 累计有效学习分钟数（自然年内） */
    private Integer yearMinutes;

    /** 目标是否来自个人设置而非默认规则，便于前端提示「已自定义」 */
    private Boolean yearTargetCustomized;
}
