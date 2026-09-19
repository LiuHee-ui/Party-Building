package com.hongmai.common.enums;

import lombok.Getter;

/** 双入口。同一账号可同时持有两端身份，但两端分别独立鉴权。 */
@Getter
public enum EntryType {

    USER("USER", "用户端"),
    ADMIN("ADMIN", "管理端");

    private final String code;
    private final String label;

    EntryType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static EntryType of(String code) {
        for (EntryType entry : values()) {
            if (entry.code.equalsIgnoreCase(code)) {
                return entry;
            }
        }
        throw new IllegalArgumentException("未知入口: " + code);
    }
}
