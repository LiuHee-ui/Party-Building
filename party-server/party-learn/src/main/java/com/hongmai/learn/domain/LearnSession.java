package com.hongmai.learn.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习时段（会话）。
 * 序号空间统一为「会话内」：用户同日重进同一资源会产生新会话，序号从 1 重新计数。
 * last_seq 是心跳幂等判定的依据。
 */
@Data
@TableName("t_learn_session")
public class LearnSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Long userId;

    private Long resourceId;

    /** 冗余组织，看板按组织聚合时免去 JOIN */
    private Long orgId;

    private LocalDateTime beginAt;

    /** 最后一次被接受的心跳时刻，限流判定的基准 */
    private LocalDateTime lastEndAt;

    /** 本会话已接受的最大 client_seq */
    private Integer lastSeq;

    private Integer unfocusedSec;

    private Integer validSec;

    /** 1 在线 / 2 断网补传 */
    private Integer source;

    private LocalDateTime reportedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
