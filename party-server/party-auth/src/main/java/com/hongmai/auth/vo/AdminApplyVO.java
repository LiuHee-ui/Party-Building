package com.hongmai.auth.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 管理员申请明细。 */
@Data
public class AdminApplyVO {

    private Long id;
    private Long userId;
    private String realName;
    private String mobileTail;
    private Long orgId;
    private List<String> proofUrls;

    /** 0 待审核 / 1 通过 / 2 驳回 */
    private Integer status;
    private String statusLabel;

    private String remark;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
}
