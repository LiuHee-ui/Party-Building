package com.hongmai.common.enums;

import lombok.Getter;

/** 角色。多角色存多行，支撑「同一账号可同时持有两端身份」。 */
@Getter
public enum RoleType {

    USER(1, "普通用户"),
    ACTIVIST(2, "积极分子"),
    ORG_ADMIN(3, "组织管理员"),
    SUPERIOR_ORG_ADMIN(4, "上级组织管理员"),
    PLATFORM_ADMIN(5, "平台管理员");

    private final int code;
    private final String label;

    RoleType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 管理端入口可进入的角色集合。 */
    public boolean canEnterAdminEntry() {
        return this == ORG_ADMIN || this == SUPERIOR_ORG_ADMIN || this == PLATFORM_ADMIN;
    }

    /** 平台管理员不受组织数据范围限制。 */
    public boolean bypassDataScope() {
        return this == PLATFORM_ADMIN;
    }
}
