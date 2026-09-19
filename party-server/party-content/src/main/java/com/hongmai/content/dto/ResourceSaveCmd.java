package com.hongmai.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 资源新增/编辑入参。
 * 字段级的「条件必填」规则（字幕、内容地址、VR 分类）不在这里用注解表达，
 * 而是集中在 ResourceValidator —— 注解无法表达跨字段依赖，硬写会散落且难测。
 */
@Data
public class ResourceSaveCmd {

    /** 编辑时传入；新增时为空 */
    private Long id;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不得超过 200 字")
    private String title;

    @Size(max = 500, message = "简介不得超过 500 字")
    private String summary;

    @Size(max = 500, message = "封面地址过长")
    private String coverUrl;

    @NotNull(message = "资源形态不能为空")
    private Integer form;

    @NotNull(message = "内容主题不能为空")
    private Integer theme;

    private Integer vrTheme;

    private Integer vrForm;

    @Size(max = 500, message = "内容地址过长")
    private String contentUrl;

    private Integer durationSec;

    @Size(max = 500, message = "字幕地址过长")
    private String subtitleUrl;

    private Integer sortNo;
}
