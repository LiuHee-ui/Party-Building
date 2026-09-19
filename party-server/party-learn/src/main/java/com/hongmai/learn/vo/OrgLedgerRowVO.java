package com.hongmai.learn.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 台账导出行。
 * 列名用 @ExcelProperty 固定，不随字段名变化——导出的表是给党委存档用的，格式要稳定。
 * 不含手机号完整号码，只有末四位。
 */
@Data
public class OrgLedgerRowVO {

    @ExcelProperty("组织")
    private String orgName;

    @ExcelProperty("姓名")
    private String realName;

    @ExcelProperty("身份")
    private String identityTypeLabel;

    @ExcelProperty("民族")
    private String ethnicity;

    @ExcelProperty("累计分钟")
    private Integer totalMinutes;

    @ExcelProperty("累计学时")
    private BigDecimal totalCredit;

    @ExcelProperty("目标学时")
    private BigDecimal targetCredit;

    @ExcelProperty("达标率")
    private String achievementText;

    @ExcelProperty("是否达标")
    private String achievedText;

    private Long userId;
}
