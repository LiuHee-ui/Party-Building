package com.hongmai.org.service.impl;

import com.hongmai.auth.spi.UserRosterProvider;
import com.hongmai.common.enums.IdentityType;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.web.PageResult;
import com.hongmai.org.domain.EthnicityRatio;
import com.hongmai.org.domain.Org;
import com.hongmai.org.dto.RosterQuery;
import com.hongmai.org.mapper.OrgMapper;
import com.hongmai.org.service.OrgService;
import com.hongmai.org.service.RosterService;
import com.hongmai.org.vo.EthnicityStatVO;
import com.hongmai.org.vo.RosterItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RosterServiceImpl implements RosterService {

    private final OrgService orgService;
    private final OrgMapper orgMapper;
    private final UserRosterProvider userRosterProvider;

    @Override
    public PageResult<RosterItemVO> pageRoster(long operatorId, RosterQuery query) {
        List<Long> scope = resolveScope(operatorId, query.getOrgId());

        PageResult<UserRosterProvider.RosterRow> page = userRosterProvider.pageRoster(
                scope, trimToNull(query.getKeyword()), trimToNull(query.getEthnicity()),
                query.getIdentityType(), Boolean.TRUE.equals(query.getOnlyApproved()), query);

        // 组织名在内存里回填，而不是在 SQL 里跨模块 JOIN —— 保持模块边界
        List<Long> orgIds = page.getRecords().stream()
                .map(UserRosterProvider.RosterRow::orgId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> orgNames = orgService.namesOf(orgIds);

        List<RosterItemVO> records = page.getRecords().stream()
                .map(row -> toVO(row, orgNames))
                .toList();
        return PageResult.of(page.getPageNo(), page.getPageSize(), page.getTotal(), records);
    }

    @Override
    public List<EthnicityStatVO> statEthnicity(long operatorId, long orgId) {
        List<Long> scope = resolveScope(operatorId, orgId == 0 ? null : orgId);

        List<UserRosterProvider.EthnicityCount> counts = userRosterProvider.countByEthnicity(scope);
        int total = EthnicityRatio.sum(counts.stream()
                .map(UserRosterProvider.EthnicityCount::count).toList());

        List<EthnicityStatVO> result = new ArrayList<>(counts.size());
        for (UserRosterProvider.EthnicityCount count : counts) {
            EthnicityStatVO vo = new EthnicityStatVO();
            vo.setEthnicity(count.ethnicity());
            vo.setCount(count.count());
            var ratio = EthnicityRatio.percent(count.count(), total);
            vo.setRatio(ratio);
            vo.setRatioText(EthnicityRatio.format(ratio));
            result.add(vo);
        }
        return result;
    }

    /**
     * 解析查询范围。
     *
     * 三层语义必须严格区分：
     *   visible == null      平台管理员，不限范围
     *   visible 为空列表      无任何可见组织 → 返回空，绝不退化成查全部
     *   指定了 orgId         必须落在可见范围内，否则 3001
     */
    private List<Long> resolveScope(long operatorId, Long requestedOrgId) {
        List<Long> visible = orgService.resolveVisibleOrgIds(operatorId);
        if (requestedOrgId == null) {
            return visible;
        }

        Org org = orgService.requireEnabled(requestedOrgId);
        List<Long> subtree = orgMapper.selectIdsUnderPath(org.getPath());

        if (visible == null) {
            return subtree;
        }
        if (visible.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        List<Long> intersected = subtree.stream().filter(visible::contains).toList();
        if (intersected.isEmpty()) {
            // 指定的组织完全落在可见范围之外 —— 明确拒绝，而不是返回空列表
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return intersected;
    }

    private RosterItemVO toVO(UserRosterProvider.RosterRow row, Map<Long, String> orgNames) {
        RosterItemVO vo = new RosterItemVO();
        vo.setUserId(row.userId());
        vo.setRealName(row.realName());
        vo.setMobileTail(row.mobileTail());
        vo.setOrgId(row.orgId());
        vo.setOrgName(row.orgId() == null ? null : orgNames.get(row.orgId()));
        vo.setIdentityType(row.identityType());
        vo.setIdentityTypeLabel(identityLabel(row.identityType()));
        vo.setEthnicity(row.ethnicity());
        vo.setLeaderFlag(row.leaderFlag());
        return vo;
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
