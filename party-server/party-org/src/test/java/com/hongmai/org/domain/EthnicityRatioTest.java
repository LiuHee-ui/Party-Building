package com.hongmai.org.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 民族占比计算测试。
 * 口径错误会直接导致「统计页数字与名册筛选结果对不上」——spec AC19 就是验这一点。
 */
class EthnicityRatioTest {

    @Test
    @DisplayName("常规占比，保留 2 位小数")
    void normalPercent() {
        assertEquals(new BigDecimal("42.86"), EthnicityRatio.percent(3, 7));
        assertEquals(new BigDecimal("50.00"), EthnicityRatio.percent(1, 2));
        assertEquals(new BigDecimal("100.00"), EthnicityRatio.percent(5, 5));
    }

    @Test
    @DisplayName("三分之一 → 33.33，保留两位不做过度舍入")
    void oneThirdPercent() {
        assertEquals(new BigDecimal("33.33"), EthnicityRatio.percent(1, 3));
    }

    @Test
    @DisplayName("分母为 0 时返回 0 而不是抛异常——空组织也会被查询")
    void zeroTotalReturnsZero() {
        assertEquals(new BigDecimal("0.00"), EthnicityRatio.percent(0, 0));
        assertEquals(new BigDecimal("0.00"), EthnicityRatio.percent(5, 0));
        assertEquals(new BigDecimal("0.00"), EthnicityRatio.percent(0, -1));
    }

    @Test
    @DisplayName("计数求和时忽略 null 与非正数")
    void sumIgnoresNullAndNonPositive() {
        List<Integer> counts = Arrays.asList(3, null, 5, 0, -2);
        assertEquals(8, EthnicityRatio.sum(counts));
    }

    @Test
    @DisplayName("空集合求和为 0")
    void sumOfEmptyIsZero() {
        assertEquals(0, EthnicityRatio.sum(Collections.emptyList()));
        assertEquals(0, EthnicityRatio.sum(null));
    }

    @Test
    @DisplayName("占比文本格式固定两位小数并带百分号")
    void formatText() {
        assertEquals("42.86%", EthnicityRatio.format(new BigDecimal("42.86")));
        assertEquals("0.00%", EthnicityRatio.format(null));
        assertEquals("100.00%", EthnicityRatio.format(new BigDecimal("100.00")));
    }
}
