package com.hongmai.learn.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 个人目标学时。
 * 存在本表记录时优先于 CreditTargetRule 的默认规则。
 */
@Data
@TableName("t_credit_target")
public class CreditTarget {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 1 自然年 / 2 五年规划期 */
    private Integer periodType;

    private String periodKey;

    private BigDecimal targetCredit;

    /** 设置人（组织管理员） */
    private Long setBy;

    private LocalDateTime setAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
