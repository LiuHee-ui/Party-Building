package com.hongmai.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理员申请审核入参。 */
@Data
public class AdminApplyReviewCmd {

    @NotNull(message = "申请单不能为空")
    private Long applyId;

    @NotNull(message = "审核结论不能为空")
    private Boolean pass;

    @Size(max = 500, message = "审核意见不得超过 500 字")
    private String remark;
}
