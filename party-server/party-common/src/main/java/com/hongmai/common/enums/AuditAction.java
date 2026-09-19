package com.hongmai.common.enums;

import lombok.Getter;

/** 审核动作。t_audit_log 只有 AuditService 的 record 与 review 两个写入入口。 */
@Getter
public enum AuditAction {

    SUBMIT("SUBMIT", true),
    APPROVE("APPROVE", false),
    REJECT("REJECT", false),
    TAKE_DOWN("TAKE_DOWN", false);

    private final String code;
    /** 是否为待审任务：SUBMIT 置 pending=1，其余处置 0。 */
    private final boolean pending;

    AuditAction(String code, boolean pending) {
        this.code = code;
        this.pending = pending;
    }
}
