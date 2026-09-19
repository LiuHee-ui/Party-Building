package com.hongmai.org.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 组织简要信息。用 record 而不是实体——避免把 t_org 的实体结构暴露到接口层。
 */
public record OrgBriefVO(Long id, String name, Integer level, String path) {

    /** 显式标注才能进 JSON：record 默认只序列化声明的分量，派生方法需要 @JsonProperty。 */
    @JsonProperty("levelLabel")
    public String levelLabel() {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case 1 -> "党委";
            case 2 -> "党总支";
            case 3 -> "党支部";
            default -> "未知";
        };
    }
}
