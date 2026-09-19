package com.hongmai.org.service.impl;

import com.hongmai.common.spi.OrgLevelProvider;
import com.hongmai.org.mapper.OrgMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 组织层级能力的实现（接口定义在 party-common）。
 *
 * 这是本项目第二处依赖倒置：party-auth 需要知道组织层级来决定授予哪一级管理员，
 * 但 party-org 依赖 party-auth，直接依赖会成环。把能力接口下沉到 common，
 * 由 party-org 实现、party-auth 按需消费。
 */
@Service
@RequiredArgsConstructor
public class OrgLevelProviderImpl implements OrgLevelProvider {

    private final OrgMapper orgMapper;

    @Override
    public int levelOf(long orgId) {
        Integer level = orgMapper.selectLevel(orgId);
        return level == null ? 0 : level;
    }
}
