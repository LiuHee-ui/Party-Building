package com.hongmai.common.enums;

import lombok.Getter;

/** 实名与管理员申请的审核状态。 */
@Getter
public enum AuditStatus {

    PENDING(0, "待审核"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已驳回"),

    /**
     * 未提交实名。与「待审核」必须区分：已提交待审核的用户不该被反复引导去填表，
     * 未提交的用户才需要。用 9 而不是 3，是为了让 0/1/2 三个审核态保持连续。
     */
    NOT_SUBMITTED(9, "未提交");

    private final int code;
    private final String label;

    AuditStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static AuditStatus of(int code) {
        for (AuditStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知审核状态: " + code);
    }
}
