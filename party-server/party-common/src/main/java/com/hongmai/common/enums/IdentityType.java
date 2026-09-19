package com.hongmai.common.enums;

import lombok.Getter;

/** 用户身份类型。台账与看板的「在册人数」口径为 1、2、3。 */
@Getter
public enum IdentityType {

    FULL_MEMBER(1, "正式党员"),
    PROBATIONARY_MEMBER(2, "预备党员"),
    ACTIVIST(3, "入党积极分子"),
    MASS(4, "群众"),
    STUDENT(5, "学生");

    private final int code;
    private final String label;

    IdentityType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static IdentityType of(int code) {
        for (IdentityType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知身份类型: " + code);
    }

    /** 是否计入台账与看板（党员 + 积极分子，不含群众与学生）。 */
    public boolean countsInRoster() {
        return this == FULL_MEMBER || this == PROBATIONARY_MEMBER || this == ACTIVIST;
    }
}
