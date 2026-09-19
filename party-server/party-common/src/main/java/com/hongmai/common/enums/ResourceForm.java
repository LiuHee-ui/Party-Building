package com.hongmai.common.enums;

import lombok.Getter;

/**
 * 学习资源形态。
 * 视频与全景须提供中文字幕（N6/AC28）；VR 类只在用户端展示简介与到站引导，不提供站内播放。
 */
@Getter
public enum ResourceForm {

    ARTICLE(1, "图文"),
    VIDEO(2, "视频"),
    PANORAMA(3, "全景"),
    VR(4, "VR");

    private final int code;
    private final String label;

    ResourceForm(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ResourceForm of(int code) {
        for (ResourceForm form : values()) {
            if (form.code == code) {
                return form;
            }
        }
        throw new IllegalArgumentException("未知资源形态: " + code);
    }

    /** 是否要求提供字幕。 */
    public boolean requiresSubtitle() {
        return this == VIDEO || this == PANORAMA;
    }

    /** 是否属于站内可播放形态（可计学习进度与学时）。 */
    public boolean playableInApp() {
        return this == ARTICLE || this == VIDEO || this == PANORAMA;
    }

    public boolean isVr() {
        return this == VR;
    }
}
