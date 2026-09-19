package com.hongmai.audit.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 待审队列条目。 */
@Data
public class AuditTaskVO {

    private Long logId;
    private Integer bizType;
    private String bizTypeLabel;
    private Long bizId;
    private Long orgId;
    private Long operatorId;

    /** 摘要文案，由业务模块在提交时提供 */
    private String summary;

    private LocalDateTime submittedAt;
}
