package com.hongmai.content.domain;

import com.hongmai.common.enums.ResourceForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 资源字段校验测试。
 * 对应 spec F5（VR 不做站内播放）、N6（视频与全景须有中文字幕）、AC28。
 * 这些规则是「审核能不能通过」的前置条件，错一条就会让不合规内容上架。
 */
class ResourceValidatorTest {

    // ---------- 图文 ----------

    @Test
    @DisplayName("图文：有内容地址即可，不要求字幕与时长")
    void articleValid() {
        assertTrue(ResourceValidator.isValid(ResourceForm.ARTICLE, null, null,
                "https://cdn/a.html", null, null));
    }

    @Test
    @DisplayName("图文：填写了字幕属于配置错误")
    void articleWithSubtitleRejected() {
        List<String> errors = ResourceValidator.violations(ResourceForm.ARTICLE, null, null,
                "https://cdn/a.html", "https://cdn/a.vtt", null);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("图文"));
    }

    @Test
    @DisplayName("图文：缺少内容地址")
    void articleWithoutContentRejected() {
        assertFalse(ResourceValidator.isValid(ResourceForm.ARTICLE, null, null, "  ", null, null));
    }

    // ---------- 视频 / 全景 ----------

    @Test
    @DisplayName("视频：内容地址、字幕、时长三者齐备")
    void videoValid() {
        assertTrue(ResourceValidator.isValid(ResourceForm.VIDEO, null, null,
                "https://cdn/v.mp4", "https://cdn/v.vtt", 600));
    }

    @Test
    @DisplayName("视频缺字幕被拒绝（AC28 的硬要求）")
    void videoWithoutSubtitleRejected() {
        List<String> errors = ResourceValidator.violations(ResourceForm.VIDEO, null, null,
                "https://cdn/v.mp4", null, 600);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("字幕"));
    }

    @Test
    @DisplayName("全景缺字幕同样被拒绝")
    void panoramaWithoutSubtitleRejected() {
        List<String> errors = ResourceValidator.violations(ResourceForm.PANORAMA, null, null,
                "https://cdn/p.jpg", null, 300);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("全景"));
    }

    @Test
    @DisplayName("视频时长非法被拒绝")
    void videoWithInvalidDurationRejected() {
        assertFalse(ResourceValidator.isValid(ResourceForm.VIDEO, null, null,
                "https://cdn/v.mp4", "https://cdn/v.vtt", 0));
        assertFalse(ResourceValidator.isValid(ResourceForm.VIDEO, null, null,
                "https://cdn/v.mp4", "https://cdn/v.vtt", null));
    }

    @Test
    @DisplayName("多项违规一次性全部返回，而不是遇错即停")
    void multipleViolationsCollected() {
        List<String> errors = ResourceValidator.violations(ResourceForm.VIDEO, null, null,
                null, null, null);
        assertEquals(3, errors.size(), "缺内容地址、缺字幕、缺时长应同时报出");
    }

    // ---------- VR ----------

    @Test
    @DisplayName("VR：只要分类齐备且无播放地址即合法")
    void vrValid() {
        assertTrue(ResourceValidator.isValid(ResourceForm.VR, 1, 2, null, null, null));
    }

    @Test
    @DisplayName("VR 缺 VR 主题或形态被拒绝")
    void vrWithoutCategoryRejected() {
        assertFalse(ResourceValidator.isValid(ResourceForm.VR, null, 2, null, null, null));
        assertFalse(ResourceValidator.isValid(ResourceForm.VR, 1, null, null, null, null));
    }

    @Test
    @DisplayName("VR 填写内容地址被拒绝——VR 类不做站内播放（spec F5）")
    void vrWithContentUrlRejected() {
        List<String> errors = ResourceValidator.violations(ResourceForm.VR, 1, 2,
                "https://cdn/vr.mp4", null, null);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("VR"));
    }

    @Test
    @DisplayName("VR 填写字幕被拒绝")
    void vrWithSubtitleRejected() {
        List<String> errors = ResourceValidator.violations(ResourceForm.VR, 1, 2,
                null, "https://cdn/vr.vtt", null);
        assertEquals(1, errors.size());
    }

    @Test
    @DisplayName("VR 同时填内容地址与字幕，两条违规都报出")
    void vrWithBothRejected() {
        List<String> errors = ResourceValidator.violations(ResourceForm.VR, 1, 2,
                "https://cdn/vr.mp4", "https://cdn/vr.vtt", null);
        assertEquals(2, errors.size());
    }

    // ---------- 形态缺失 ----------

    @Test
    @DisplayName("形态为空时只报这一条，不做后续判断")
    void nullFormRejected() {
        List<String> errors = ResourceValidator.violations(null, 1, 2, null, null, null);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("形态"));
    }
}
