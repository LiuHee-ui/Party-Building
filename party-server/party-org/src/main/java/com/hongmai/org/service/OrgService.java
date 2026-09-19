package com.hongmai.org.service;

import com.hongmai.org.domain.Org;

import java.util.List;

public interface OrgService {

    /**
     * 生成组织祖先路径。
     * parentId = 0 表示根组织，返回 /{orgId}/；否则取上级 path 拼接。
     *
     * @throws com.hongmai.common.exception.BizException 4007 上级组织不存在或已停用
     */
    String buildPath(long orgId, long parentId);

    /**
     * 解析当前操作员可见的组织 id 列表。
     *
     * @return null 表示不限范围（平台管理员）；空列表表示无任何可见组织。
     *         调用方必须区分这两种情况——把空列表当成「不加条件」会导致越权。
     *
     * 组织来源取 LoginContext.primaryOrgId（由拦截器从 Token 解析并注入），
     * 不跨模块查询 t_user，避免 org 与 auth 之间产生双向耦合。
     */
    List<Long> resolveVisibleOrgIds(long operatorId);

    /** 取启用状态的组织，不存在或停用抛 4007。 */
    Org requireEnabled(long orgId);

    /** 批量取组织名称，返回 id → name。用于名册回填，避免在 SQL 里跨模块 JOIN。 */
    java.util.Map<Long, String> namesOf(List<Long> orgIds);

    /**
     * 取操作员可见的组织列表，供前端组织选择器使用。
     * 平台管理员返回全部启用组织；无可见组织时返回空列表。
     */
    List<Org> listVisible(long operatorId);

    /**
     * 取某路径子树下的全部启用组织 id（含自身）。
     * 供其他模块做「指定组织范围」解析时复用——避免各模块自己写一遍路径前缀匹配。
     */
    List<Long> idsUnderPath(String path);
}
