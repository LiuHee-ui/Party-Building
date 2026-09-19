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

/** 学习记录（用户 × 资源聚合）。 */
@Data
@TableName("t_learn_record")
public class LearnRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resourceId;

    private Long orgId;

    /** 上次进度位置，续播用 */
    private Integer lastPositionSec;

    private BigDecimal progressPct;

    private Integer validSec;

    private Integer unreadFlag;

    private LocalDateTime firstStartAt;

    private LocalDateTime lastLearnAt;

    private LocalDateTime finishAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
