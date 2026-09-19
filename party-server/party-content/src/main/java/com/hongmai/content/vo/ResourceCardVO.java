package com.hongmai.content.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 用户端资源卡片。 */
@Data
public class ResourceCardVO {

    private Long id;
    private String title;
    private String summary;
    private String coverUrl;

    private Integer form;
    private String formLabel;

    private Integer theme;
    private String themeLabel;

    private Integer vrTheme;
    private Integer vrForm;

    private Integer durationSec;

    /** 当前用户完成度，未登录或未学习时为空 */
    private BigDecimal progressPct;
}
