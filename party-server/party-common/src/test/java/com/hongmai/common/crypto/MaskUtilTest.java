package com.hongmai.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 脱敏展示测试（承接 spec N1「手机号在列表展示时脱敏」）。 */
class MaskUtilTest {

    @Test
    @DisplayName("取末四位")
    void tail4() {
        assertEquals("8000", MaskUtil.tail4("13800138000"));
    }

    @Test
    @DisplayName("长度不足四位时原样返回")
    void tail4ShortInput() {
        assertEquals("123", MaskUtil.tail4("123"));
    }

    @Test
    @DisplayName("空值与空白返回 null")
    void tail4NullSafe() {
        assertNull(MaskUtil.tail4(null));
        assertNull(MaskUtil.tail4("   "));
    }

    @Test
    @DisplayName("手机号掩码保留前三后四")
    void maskMobile() {
        assertEquals("138****8000", MaskUtil.maskMobile("13800138000"));
    }

    @Test
    @DisplayName("过短的号码退化为仅保留末四位")
    void maskMobileShortInput() {
        assertEquals("1234", MaskUtil.maskMobile("1234"));
    }

    @Test
    @DisplayName("姓名掩码仅保留姓氏")
    void maskName() {
        assertEquals("张**", MaskUtil.maskName("张三丰"));
        assertEquals("李", MaskUtil.maskName("李"));
        assertNull(MaskUtil.maskName(null));
    }
}
