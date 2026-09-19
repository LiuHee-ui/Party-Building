package com.hongmai.org.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 少数民族党员统计条目。 */
@Data
public class EthnicityStatVO {

    private String ethnicity;

    private Integer count;

    /** 占比，保留 2 位小数。分母为范围内已通过实名审核的党员与积极分子总数。 */
    private BigDecimal ratio;

    /** 占比展示，如 42.86% */
    private String ratioText;
}
