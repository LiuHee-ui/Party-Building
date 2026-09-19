package com.hongmai.org.dto;

import com.hongmai.common.web.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 名册查询条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RosterQuery extends PageQuery {

    /** 组织节点；不传时取操作员所在组织 */
    private Long orgId;

    /** 姓名模糊检索，最长 20 字 */
    private String keyword;

    /** 民族筛选，如「藏族」 */
    private String ethnicity;

    /** 身份类型筛选：1 正式党员 / 2 预备党员 / 3 积极分子 / 4 群众 / 5 学生 */
    private Integer identityType;

    /** 是否只取已通过实名审核的，默认 true —— 台账口径就是「已通过」 */
    private Boolean onlyApproved = Boolean.TRUE;
}
