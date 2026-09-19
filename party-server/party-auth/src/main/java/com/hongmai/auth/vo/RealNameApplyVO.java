package com.hongmai.auth.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 待审核实名申请明细。 */
@Data
public class RealNameApplyVO {

    private Long userId;
    private String realName;

    /** 仅末四位，全部号码需解密并写敏感访问日志，列表场景不展示 */
    private String mobileTail;

    private Long orgId;
    private Integer identityType;
    private String identityTypeLabel;
    private String ethnicity;
    private LocalDateTime submittedAt;
}
