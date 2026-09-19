package com.hongmai.common.enums;

import lombok.Getter;

/** VR 内容主题，仅 form=VR 的资源适用。 */
@Getter
public enum VrTheme {

    REVOLUTIONARY_WAR(1, "革命战争"),
    CONSTRUCTION_ACHIEVEMENT(2, "建设成就"),
    HISTORICAL_FIGURE(3, "历史人物"),
    PARTY_SPIRIT(4, "党性修养");

    private final int code;
    private final String label;

    VrTheme(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static VrTheme of(int code) {
        for (VrTheme theme : values()) {
            if (theme.code == code) {
                return theme;
            }
        }
        throw new IllegalArgumentException("未知 VR 主题: " + code);
    }
}
