package com.hongmai.common.enums;

import lombok.Getter;

/** VR 内容形态，对应「观影、体验、交互」三位一体。 */
@Getter
public enum VrForm {

    ROAMING(1, "漫游类"),
    EXHIBITION(2, "展馆类"),
    INTERACTIVE(3, "交互类");

    private final int code;
    private final String label;

    VrForm(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static VrForm of(int code) {
        for (VrForm form : values()) {
            if (form.code == code) {
                return form;
            }
        }
        throw new IllegalArgumentException("未知 VR 形态: " + code);
    }
}
