package com.hongmai.content.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习资源。
 *
 * 不变式由 ResourceValidator 保证：
 *   form = VR 时 vrTheme/vrForm 必填、contentUrl 与 subtitleUrl 必须为空；
 *   form ∈ {视频, 全景} 时 subtitleUrl 与 durationSec 必填。
 * subtitleLang 恒为 zh-Hans —— 字幕语言不随界面语言切换（spec N6）。
 */
@Data
@TableName("t_resource")
public class Resource {

    public static final String DEFAULT_SUBTITLE_LANG = "zh-Hans";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String summary;

    private String coverUrl;

    /** 1 图文 / 2 视频 / 3 全景 / 4 VR */
    private Integer form;

    /** 1 党的历史 / 2 理论知识 / 3 先进事迹 */
    private Integer theme;

    /** VR 专属：1 革命战争 / 2 建设成就 / 3 历史人物 / 4 党性修养 */
    private Integer vrTheme;

    /** VR 专属：1 漫游类 / 2 展馆类 / 3 交互类 */
    private Integer vrForm;

    /** 内容地址；form=VR 时必须为空 */
    private String contentUrl;

    private Integer durationSec;

    /** 中文字幕地址；form ∈ {视频, 全景} 时必填 */
    private String subtitleUrl;

    private String subtitleLang;

    /** 0 草稿 / 1 待审核 / 2 已上架 / 3 已下架 */
    private Integer status;

    private Integer sortNo;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
