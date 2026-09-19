package com.hongmai.auth.spi;

import com.hongmai.common.web.PageQuery;
import com.hongmai.common.web.PageResult;

import java.util.List;

/**
 * 名册数据查询能力（由 t_user 的归属方 party-auth 提供）。
 *
 * 为什么做成接口而不是让 party-org 直接查 t_user：
 * 跨模块只允许调用对方 service 层的对外接口，禁止直接使用对方的 mapper 与实体。
 * 名册的「组织范围解析」与「组织名回填」属于 party-org 的职责，
 * 而「党员与积极分子数据」属于 party-auth 的职责，因此按职责拆成两侧：
 * auth 提供原始行数据，org 负责按组织范围编排与装配。
 *
 * 返回的 RosterRow 是只读投影，不是 auth 的实体——避免实体跨模块泄露。
 */
public interface UserRosterProvider {

    /** 名册一行。只含展示所需字段，不含任何敏感原文。 */
    record RosterRow(
            Long userId,
            String realName,
            /** 仅末四位，完整号码需解密并写敏感访问日志 */
            String mobileTail,
            Long orgId,
            Integer identityType,
            String ethnicity,
            Integer leaderFlag
    ) {
    }

    /** 民族分组计数。 */
    record EthnicityCount(String ethnicity, int count) {
    }

    /**
     * 按组织范围分页查询名册。
     *
     * @param orgIds       可见组织 id；null 表示不限范围，空列表表示无可见组织（必须返回空）
     * @param keyword      姓名模糊检索，可为空
     * @param ethnicity    民族筛选，可为空
     * @param identityType 身份类型筛选，可为空
     * @param onlyApproved 是否只取已通过实名审核的，默认 true
     */
    PageResult<RosterRow> pageRoster(List<Long> orgIds, String keyword, String ethnicity,
                                     Integer identityType, boolean onlyApproved, PageQuery query);

    /**
     * 按民族统计范围内人数。
     * 分母口径：identity_type IN (1,2,3) 且 audit_status = 1，即党员与积极分子，
     * 不含群众与学生——与台账口径必须一致，否则看板与明细对不上。
     */
    List<EthnicityCount> countByEthnicity(List<Long> orgIds);

    /**
     * 按 id 批量取名册行，用于台账导出这类「已有 userId 列表、需要回填姓名」的场景。
     * 入参为空时返回空列表，不查库。
     */
    List<RosterRow> findByIds(List<Long> userIds);

    /**
     * 在册人数。
     * 口径固定为 identity_type IN (1,2,3) 且 audit_status = 1，与名册、民族统计完全一致——
     * 看板的「在册人数」与台账列表条数必须能对上，否则用户一眼就能发现数据不可信。
     *
     * @param orgIds null 表示不限范围，空列表表示无可见组织（返回 0）
     */
    long countRoster(List<Long> orgIds);
}
