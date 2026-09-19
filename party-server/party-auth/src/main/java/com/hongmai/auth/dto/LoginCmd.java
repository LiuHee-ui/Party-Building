package com.hongmai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 微信登录入参。 */
@Data
public class LoginCmd {

    @NotBlank(message = "微信登录凭证不能为空")
    private String jsCode;
}
