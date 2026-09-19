package com.hongmai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 管理员注册申请入参。 */
@Data
public class AdminApplyCmd {

    @NotBlank(message = "姓名不能为空")
    @Size(min = 2, max = 50, message = "姓名长度须为 2-50 字")
    private String realName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "1[3-9]\\d{9}", message = "手机号格式不正确")
    private String mobile;

    @NotNull(message = "申请管理的组织不能为空")
    private Long orgId;

    /** 身份证明图片对象键，1-5 张 */
    @NotEmpty(message = "请至少上传一张身份证明")
    @Size(max = 5, message = "身份证明最多 5 张")
    private List<String> proofUrls;
}
