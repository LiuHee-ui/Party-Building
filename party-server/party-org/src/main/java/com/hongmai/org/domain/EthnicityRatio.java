package com.hongmai.org.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 民族占比计算。纯函数，便于单测——占比口径出错会直接导致看板与台账对不上账。 */
public final class EthnicityRatio {

    private EthnicityRatio() {
    }

    /**
     * 计算占比，单位百分比，保留 2 位小数（HALF_UP）。
     * 分母为 0 时返回 0，不抛异常——空组织也会被查询，不能因此报错。
     */
    public static BigDecimal percent(int count, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    public static int sum(List<Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Integer count : counts) {
            if (count != null && count > 0) {
                total += count;
            }
        }
        return total;
    }

    public static String format(BigDecimal percent) {
        return percent == null ? "0.00%" : percent.toPlainString() + "%";
    }
}
