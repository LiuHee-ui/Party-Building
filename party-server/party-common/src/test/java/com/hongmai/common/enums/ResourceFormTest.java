package com.hongmai.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 资源形态规则测试。
 * 对应 spec N6/AC28「视频与全景须提供字幕」、F5「VR 类不做站内播放」。
 */
class ResourceFormTest {

    @Test
    @DisplayName("视频与全景要求字幕")
    void subtitleRequiredForVideoAndPanorama() {
        assertTrue(ResourceForm.VIDEO.requiresSubtitle());
        assertTrue(ResourceForm.PANORAMA.requiresSubtitle());
        assertFalse(ResourceForm.ARTICLE.requiresSubtitle());
        assertFalse(ResourceForm.VR.requiresSubtitle());
    }

    @Test
    @DisplayName("图文/视频/全景可站内播放并计学时，VR 不可")
    void playableInApp() {
        assertTrue(ResourceForm.ARTICLE.playableInApp());
        assertTrue(ResourceForm.VIDEO.playableInApp());
        assertTrue(ResourceForm.PANORAMA.playableInApp());
        assertFalse(ResourceForm.VR.playableInApp());
    }

    @Test
    @DisplayName("VR 形态标识")
    void vrFlag() {
        assertTrue(ResourceForm.VR.isVr());
        assertFalse(ResourceForm.ARTICLE.isVr());
    }
}
