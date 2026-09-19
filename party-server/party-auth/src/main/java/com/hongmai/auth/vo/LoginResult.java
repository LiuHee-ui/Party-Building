package com.hongmai.auth.vo;

import lombok.Data;

import java.util.List;

/** 登录结果。 */
@Data
public class LoginResult {

    private String token;

    /**
     * 0 待审核 / 1 通过 / 2 驳回 / 9 未提交。
     * 未提交时前端才引导去实名页——已提交待审核的用户不该被反复要求填表。
     */
    private Integer realNameStatus;

    /** 该账号可进入的入口列表。普通用户只有 USER，管理员两种都有。 */
    private List<EntryVO> entries;

    private String nickname;

    private String avatarUrl;

    /** 是否为首次建档（刚注册还没实名），前端据此决定直接进实名页还是首页。 */
    private Boolean firstTime;
}
