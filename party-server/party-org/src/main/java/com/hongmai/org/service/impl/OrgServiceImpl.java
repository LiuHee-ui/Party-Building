package com.hongmai.org.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongmai.common.enums.RoleType;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.util.OrgPathUtil;
import com.hongmai.common.web.LoginContext;
import com.hongmai.org.domain.Org;
import com.hongmai.org.mapper.OrgMapper;
import com.hongmai.org.service.OrgService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrgServiceImpl implements OrgService {

    private final OrgMapper orgMapper;

    @Override
    public String buildPath(long orgId, long parentId) {
        if (parentId <= 0) {
            return OrgPathUtil.buildPath(null, orgId);
        }
        Org parent = orgMapper.selectById(parentId);
        if (parent == null || !parent.isEnabled()) {
            throw new BizException(ErrorCode.ORG_NOT_FOUND, "上级组织不存在或已停用");
        }
        return OrgPathUtil.buildPath(parent.getPath(), orgId);
    }

    @Override
    public List<Long> resolveVisibleOrgIds(long operatorId) {
        LoginContext context = LoginContext.get();
        if (context == null) {
            // 无请求上下文（如定时任务）时不放行任何数据，宁缺勿滥
            log.warn("无登录上下文，数据范围解析返回空 operatorId={}", operatorId);
            return Collections.emptyList();
        }

        if (context.hasRole(RoleType.PLATFORM_ADMIN.getCode())) {
            return null;
        }

        long orgId = context.getPrimaryOrgId();
        if (orgId <= 0) {
            return Collections.emptyList();
        }

        Org org = orgMapper.selectById(orgId);
        if (org == null || !org.isEnabled()) {
            return Collections.emptyList();
        }
        return orgMapper.selectIdsUnderPath(org.getPath());
    }

    @Override
    public Org requireEnabled(long orgId) {
        Org org = orgMapper.selectById(orgId);
        if (org == null || !org.isEnabled()) {
            throw new BizException(ErrorCode.ORG_NOT_FOUND);
        }
        return org;
    }

    @Override
    public Map<Long, String> namesOf(List<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Org> orgs = orgMapper.selectNamesByIds(orgIds);
        Map<Long, String> result = new HashMap<>(orgs.size());
        for (Org org : orgs) {
            result.put(org.getId(), org.getName());
        }
        return result;
    }

    @Override
    public List<Org> listVisible(long operatorId) {
        List<Long> visible = resolveVisibleOrgIds(operatorId);
        if (visible != null && visible.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Org> wrapper = new LambdaQueryWrapper<Org>()
                .eq(Org::getStatus, 1)
                .orderByAsc(Org::getPath);
        if (visible != null) {
            wrapper.in(Org::getId, visible);
        }
        return orgMapper.selectList(wrapper);
    }

    @Override
    public List<Long> idsUnderPath(String path) {
        return orgMapper.selectIdsUnderPath(OrgPathUtil.normalize(path));
    }
}
