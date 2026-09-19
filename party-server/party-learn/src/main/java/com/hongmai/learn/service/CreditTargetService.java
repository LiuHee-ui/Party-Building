package com.hongmai.learn.service;

import com.hongmai.common.enums.PeriodType;

import java.math.BigDecimal;

public interface CreditTargetService {

    /**
     * 解析某用户在某周期的目标学时。
     * 优先级：个人设置（t_credit_target）> 默认规则（书记 56/280，普通 32/160）。
     */
    BigDecimal resolveTarget(long userId, boolean leader, PeriodType periodType, String periodKey);

    /** 目标值是否来自个人设置。前端据此显示「已自定义」。 */
    boolean isCustomized(long userId, PeriodType periodType, String periodKey);

    /**
     * 设置个人目标学时。
     *
     * @throws com.hongmai.common.exception.BizException 4010 目标学时必须大于 0
     */
    void setTarget(long operatorId, long userId, PeriodType periodType, String periodKey,
                   BigDecimal target);
}
