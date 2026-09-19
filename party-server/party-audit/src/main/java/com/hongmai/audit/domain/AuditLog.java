package com.hongmai.audit.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核流水。
 *
 * 设计要点：待审队列就是本表 pending=1 的记录，
 * 因此 party-audit 不需要访问任何业务表即可提供待审列表——这是依赖倒置能成立的关键。
 */
@Data
@TableName("t_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 见 AuditBizType：1 学习资源 / 2 用户实名 / 3 管理员申请 / 4 用户内容 */
    private Integer bizType;

    private Long bizId;

    /** 归属组织，待审队列按数据范围过滤用 */
    private Long orgId;

    /** 待审队列展示用摘要，由业务模块在提交时提供 */
    private String summary;

    /** 见 AuditAction：SUBMIT / APPROVE / REJECT / TAKE_DOWN */
    private String action;

    /** 1 仍是待审任务 / 0 已处理 */
    private Integer pending;

    private Integer fromStatus;

    private Integer toStatus;

    private Long operatorId;

    /** 审核意见，REJECT 时必填 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
