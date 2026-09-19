package com.hongmai.common.enums;

import lombok.Getter;

/** 排行榜维度。个人榜按所属组织分片存 Redis，组织榜按组织节点聚合。 */
@Getter
public enum RankingScopeType {

    USER(1, "个人"),
    ORG(2, "组织");

    private final int code;
    private final String label;

    RankingScopeType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static RankingScopeType of(int code) {
        for (RankingScopeType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知排行榜维度: " + code);
    }
}
