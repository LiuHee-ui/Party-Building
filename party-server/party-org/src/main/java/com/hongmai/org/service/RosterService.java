package com.hongmai.org.service;

import com.hongmai.org.dto.RosterQuery;
import com.hongmai.org.vo.EthnicityStatVO;
import com.hongmai.org.vo.RosterItemVO;
import com.hongmai.common.web.PageResult;

import java.util.List;

public interface RosterService {

    /**
     * 按数据范围分页查询名册。
     * 若指定 orgId，必须落在操作员可见范围内，否则报 3001。
     */
    PageResult<RosterItemVO> pageRoster(long operatorId, RosterQuery query);

    /**
     * 少数民族党员统计。
     * 分母为范围内 identity_type IN (1,2,3) 且 audit_status=1 的人数，
     * 与 pageRoster 的默认口径一致——口径不一致会导致「统计页数字与筛选结果不符」。
     */
    List<EthnicityStatVO> statEthnicity(long operatorId, long orgId);
}
