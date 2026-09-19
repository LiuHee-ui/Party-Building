package com.hongmai.learn.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学时流水。
 * uk_credit_source(user_id, period_type, period_key, source_type, source_id) 保证
 * 同一天同一资源的折算分录只有一条，重复折算不会重复入账。
 */
@Data
@TableName("t_credit")
public class Credit {

    /** 来源类型：本期只有 1 学习。预留考试折算等后续来源。 */
    public static final int SOURCE_LEARN = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long orgId;

    /** 1 自然年 / 2 五年规划期 */
    private Integer periodType;

    /** 2026 或 2021-2025 */
    private String periodKey;

    private Integer sourceType;

    /** 来源记录 id（t_credit_daily.id），配合唯一键实现幂等 */
    private Long sourceId;

    private Integer minutes;

    /** 折算学时 = minutes / 45 */
    private BigDecimal credit;

    private LocalDate occurredOn;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
