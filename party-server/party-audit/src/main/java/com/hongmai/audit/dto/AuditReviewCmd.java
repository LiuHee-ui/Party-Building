package com.hongmai.audit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 审核入参。 */
@Data
public class AuditReviewCmd {

    @NotNull(message = "可审对象类型不能为空")
    private Integer bizType;

    @NotNull(message = "业务主键不能为空")
    private Long bizId;

    @NotNull(message = "审核结论不能为空")
    private Boolean pass;

    /** 驳回时必填 */
    @Size(max = 500, message = "审核意见不得超过 500 字")
    private String remark;
}
