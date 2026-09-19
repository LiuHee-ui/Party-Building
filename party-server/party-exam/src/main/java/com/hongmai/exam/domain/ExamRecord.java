package com.hongmai.exam.domain;

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
 * 答题记录。
 * uk_user_paper(user_id, paper_id) 保证一份试卷只能提交一次——
 * 这条唯一约束就是「重复提交返回 4002」的实现基础。
 */
@Data
@TableName("t_exam_record")
public class ExamRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long paperId;

    private Long orgId;

    private BigDecimal score;

    private Integer correctCount;

    private Integer totalCount;

    /** 服务端判分明细，落库的是判定结果而不是用户提交的原始答案 */
    private String detailJson;

    private LocalDateTime submittedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
