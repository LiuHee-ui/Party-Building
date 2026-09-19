package com.hongmai.learn.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学时日累计。
 * uk_user_resource_date 保证一天一资源只有一行，配合 minutes <= 120 实现单日封顶。
 */
@Data
@TableName("t_credit_daily")
public class CreditDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resourceId;

    private Long orgId;

    /** 统计日，按 Asia/Shanghai 切日 */
    private LocalDate statDate;

    /** 当日该资源有效学习分钟数，封顶 120 */
    private Integer minutes;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
