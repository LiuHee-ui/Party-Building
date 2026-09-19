package com.hongmai.exam.service;

import com.hongmai.exam.vo.DashboardVO;

public interface DashboardService {

    /**
     * 管理端看板五指标。
     *
     * @param orgId    组织节点，0 表示取操作员可见范围
     * @param yearFrom 统计区间起（含）
     * @param yearTo   统计区间止（含）
     * @throws com.hongmai.common.exception.BizException 3001 指定组织不在可见范围内
     */
    DashboardVO dashboard(long operatorId, long orgId, java.time.LocalDate yearFrom,
                          java.time.LocalDate yearTo);
}
