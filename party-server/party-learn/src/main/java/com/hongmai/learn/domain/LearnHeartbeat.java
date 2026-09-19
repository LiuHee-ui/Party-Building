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
 * 学习心跳明细。
 * uk_session_seq(session_id, client_seq) 是断网补传重复上报的幂等键——
 * 这是本项目里「用唯一约束承载业务规则」最典型的一处。
 */
@Data
@TableName("t_learn_heartbeat")
public class LearnHeartbeat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Integer clientSeq;

    private Integer deltaSec;

    private Integer focusedSec;

    private Integer positionSec;

    /** 服务端认定实际计入的有效秒数，可能因限额小于 focusedSec */
    private Integer acceptedSec;

    /** REPLAY / OUT_OF_ORDER / RATE_LIMITED / OVER_DELTA */
    private String rejectReason;

    private LocalDateTime reportedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
