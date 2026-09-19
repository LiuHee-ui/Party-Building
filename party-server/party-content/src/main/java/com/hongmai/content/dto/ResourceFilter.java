package com.hongmai.content.dto;

import lombok.Data;

/** 用户端资源检索条件。 */
@Data
public class ResourceFilter {

    /** 资源形态：1 图文 / 2 视频 / 3 全景 / 4 VR */
    private Integer form;

    /** 内容主题：1 党的历史 / 2 理论知识 / 3 先进事迹 */
    private Integer theme;

    /** VR 主题：1 革命战争 / 2 建设成就 / 3 历史人物 / 4 党性修养 */
    private Integer vrTheme;

    /** VR 形态：1 漫游类 / 2 展馆类 / 3 交互类 */
    private Integer vrForm;

    /** 标题或简介关键词，最长 30 字 */
    private String keyword;
}
