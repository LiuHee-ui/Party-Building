package com.hongmai.exam.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 试卷。question_ids_json 存题目 id、题型、分值与标准答案——标准答案只存在于服务端。 */
@Data
@TableName("t_exam_paper")
public class ExamPaper {

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** 关联学习资源，可为空（独立测验） */
    private Long resourceId;

    /** JSON 数组：[{id,type,score,correctOptions}] */
    private String questionIdsJson;

    private Integer totalScore;

    /** 0 草稿 / 1 启用 / 2 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public boolean isEnabled() {
        return status != null && status == STATUS_ENABLED;
    }
}
