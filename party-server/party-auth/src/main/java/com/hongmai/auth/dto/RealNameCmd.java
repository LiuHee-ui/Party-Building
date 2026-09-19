package com.hongmai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 实名信息提交入参。 */
@Data
public class RealNameCmd {

    @NotBlank(message = "姓名不能为空")
    @Size(min = 2, max = 50, message = "姓名长度须为 2-50 字")
    private String realName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "1[3-9]\\d{9}", message = "手机号格式不正确")
    private String mobile;

    @NotNull(message = "所属组织不能为空")
    private Long orgId;

    /** 1 正式党员 / 2 预备党员 / 3 积极分子 / 4 群众 / 5 学生 */
    @NotNull(message = "身份类型不能为空")
    private Integer identityType;

    @NotBlank(message = "民族不能为空")
    @Size(max = 20, message = "民族名称过长")
    private String ethnicity;

    @Size(max = 255, message = "头像地址过长")
    private String avatarUrl;

    /** 是否党组织书记或班子成员，影响默认目标学时 */
    private Boolean leader;
}
