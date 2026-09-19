package com.hongmai.common.crypto;

/** 脱敏工具。列表与详情统一取末四位，不触发敏感访问日志。 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /** 取手机号末四位，长度不足时原样返回。 */
    public static String tail4(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return null;
        }
        return mobile.length() <= 4 ? mobile : mobile.substring(mobile.length() - 4);
    }

    /** 掩码展示，如 138****8888；长度不足 7 位时退化为仅保留末四位。 */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return null;
        }
        if (mobile.length() < 7) {
            return tail4(mobile);
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    /** 姓名掩码，仅保留姓氏，如 张**。 */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (name.length() == 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }
}
