package com.hongmai.content.vo;

import lombok.Data;

/** 用户端资源详情。 */
@Data
public class ResourceDetailVO {

    private Long id;
    private String title;
    private String summary;
    private String coverUrl;

    /** form=VR 时恒为空 —— VR 类只在用户端展示简介与到站引导，不提供站内播放 */
    private String contentUrl;

    private Integer form;
    private String formLabel;
    private Integer theme;
    private String themeLabel;
    private Integer vrTheme;
    private String vrThemeLabel;
    private Integer vrForm;
    private String vrFormLabel;
    private Integer durationSec;

    /** 中文字幕地址，form ∈ {视频, 全景} 时非空 */
    private String subtitleUrl;

    /** 字幕语言，固定 zh-Hans，不随界面语言切换 */
    private String subtitleLang;

    /** form=VR 时的到站体验引导文案；其余形态为空 */
    private String experienceHint;

    /** 续播位置（秒） */
    private Integer lastPositionSec;
}
