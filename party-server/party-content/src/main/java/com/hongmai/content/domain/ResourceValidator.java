package com.hongmai.content.domain;

import com.hongmai.common.enums.ResourceForm;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 学习资源字段校验。
 *
 * 抽成纯函数而不是写在 Service 里，有两个原因：
 *   1. 校验规则是业务约束的核心（字幕必填、VR 不含播放地址），值得独立单测
 *   2. 返回全部违规项而不是遇错即抛，方便前端一次性提示所有问题
 *
 * 规则来源（spec F5 / F6 / N6 / AC28 与 plan §4.5 不变式）：
 *   form = VR          → vrTheme、vrForm 必填；contentUrl 必须为空；subtitleUrl 必须为空
 *   form = VIDEO/全景   → contentUrl、subtitleUrl、durationSec 必填
 *   form = 图文        → contentUrl 必填；subtitleUrl 必须为空；durationSec 可空
 */
public final class ResourceValidator {

    private ResourceValidator() {
    }

    public static List<String> violations(ResourceForm form, Integer vrTheme, Integer vrForm,
                                          String contentUrl, String subtitleUrl, Integer durationSec) {
        List<String> errors = new ArrayList<>();
        if (form == null) {
            errors.add("资源形态不能为空");
            return errors;
        }

        if (form.isVr()) {
            if (vrTheme == null) {
                errors.add("VR 资源必须指定 VR 主题");
            }
            if (vrForm == null) {
                errors.add("VR 资源必须指定 VR 形态");
            }
            if (StringUtils.hasText(contentUrl)) {
                errors.add("VR 资源不提供站内播放，内容地址必须为空");
            }
            if (StringUtils.hasText(subtitleUrl)) {
                errors.add("VR 资源不提供站内播放，不应填写字幕");
            }
            return errors;
        }

        if (!StringUtils.hasText(contentUrl)) {
            errors.add("非 VR 资源必须提供内容地址");
        }

        if (form.requiresSubtitle()) {
            if (!StringUtils.hasText(subtitleUrl)) {
                errors.add(form.getLabel() + "资源必须提供中文字幕");
            }
            if (durationSec == null || durationSec <= 0) {
                errors.add(form.getLabel() + "资源必须提供有效时长");
            }
        } else if (StringUtils.hasText(subtitleUrl)) {
            errors.add("图文资源不应填写字幕");
        }

        return errors;
    }

    /** 是否通过校验。 */
    public static boolean isValid(ResourceForm form, Integer vrTheme, Integer vrForm,
                                  String contentUrl, String subtitleUrl, Integer durationSec) {
        return violations(form, vrTheme, vrForm, contentUrl, subtitleUrl, durationSec).isEmpty();
    }
}
