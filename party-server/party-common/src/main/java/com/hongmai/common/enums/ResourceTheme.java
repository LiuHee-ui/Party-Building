package com.hongmai.common.enums;

import lombok.Getter;

/** 内容主题（资源分类的第一维度）。 */
@Getter
public enum ResourceTheme {

    PARTY_HISTORY(1, "党的历史"),
    THEORY(2, "理论知识"),
    EXEMPLARY_DEEDS(3, "先进事迹");

    private final int code;
    private final String label;

    ResourceTheme(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ResourceTheme of(int code) {
        for (ResourceTheme theme : values()) {
            if (theme.code == code) {
                return theme;
            }
        }
        throw new IllegalArgumentException("未知内容主题: " + code);
    }
}
