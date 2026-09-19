package com.hongmai.auth.service.impl;

import com.hongmai.auth.domain.User;
import com.hongmai.auth.mapper.UserMapper;
import com.hongmai.auth.spi.UserRosterProvider;
import com.hongmai.common.web.PageQuery;
import com.hongmai.common.web.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 名册数据提供者的默认实现。只做数据投影，不做组织范围判定（那是 party-org 的职责）。 */
@Service
@RequiredArgsConstructor
public class UserRosterProviderImpl implements UserRosterProvider {

    private final UserMapper userMapper;

    @Override
    public PageResult<RosterRow> pageRoster(List<Long> orgIds, String keyword, String ethnicity,
                                           Integer identityType, boolean onlyApproved, PageQuery query) {
        // 空列表代表「无任何可见组织」，必须返回空而不是退化成查全部
        if (orgIds != null && orgIds.isEmpty()) {
            return PageResult.empty(query.getPageNo(), query.getPageSize());
        }

        List<User> rows = userMapper.selectRoster(orgIds, keyword, ethnicity, identityType,
                onlyApproved, query.getOffset(), query.getPageSize());
        long total = userMapper.countRoster(orgIds, keyword, ethnicity, identityType, onlyApproved);

        List<RosterRow> records = rows.stream().map(this::toRow).toList();
        return PageResult.of(query.getPageNo(), query.getPageSize(), total, records);
    }

    private RosterRow toRow(User user) {
        return new RosterRow(user.getId(), user.getRealName(), user.getMobileTail(),
                user.getOrgId(), user.getIdentityType(), user.getEthnicity(), user.getLeaderFlag());
    }

    @Override
    public List<EthnicityCount> countByEthnicity(List<Long> orgIds) {
        if (orgIds != null && orgIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = userMapper.statEthnicity(orgIds);
        List<EthnicityCount> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object ethnicity = row.get("ethnicity");
            Object cnt = row.get("cnt");
            if (ethnicity == null || cnt == null) {
                continue;
            }
            result.add(new EthnicityCount(ethnicity.toString(), ((Number) cnt).intValue()));
        }
        return result;
    }

    @Override
    public List<RosterRow> findByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectRosterByIds(userIds).stream().map(this::toRow).toList();
    }

    @Override
    public long countRoster(List<Long> orgIds) {
        if (orgIds != null && orgIds.isEmpty()) {
            return 0L;
        }
        return userMapper.countRosterInScope(orgIds);
    }
}
