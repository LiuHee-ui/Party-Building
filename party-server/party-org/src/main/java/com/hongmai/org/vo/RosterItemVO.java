package com.hongmai.org.vo;

import lombok.Data;

/** 名册条目。 */
@Data
public class RosterItemVO {

    private Long userId;
    private String realName;

    /** 仅末四位 —— 完整号码需要解密并写敏感访问日志，列表场景不给 */
    private String mobileTail;

    private Long orgId;
    private String orgName;

    private Integer identityType;
    private String identityTypeLabel;

    private String ethnicity;

    /** 1 = 党组织书记或班子成员，决定默认目标学时 */
    private Integer leaderFlag;

    /**
     * 当期累计学时。
     * 数据属 party-learn，由学习模块通过 SPI 回填；学习模块未就位时保持为空。
     * 不在 party-org 里跨模块查询，避免 org 与 learn 形成依赖环。
     */
    private java.math.BigDecimal totalCredit;
}
