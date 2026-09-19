package com.hongmai.common.enums;

import lombok.Getter;

/** 可审对象类型。目标表与合法迁移见 plan §5.2 的 AuditBizType 映射表。 */
@Getter
public enum AuditBizType {

    RESOURCE(1, "学习资源", "t_resource", "status"),
    REAL_NAME(2, "用户实名", "t_user", "audit_status"),
    ADMIN_APPLY(3, "管理员申请", "t_admin_application", "status"),
    USER_CONTENT(4, "用户内容", "t_comment", "status");

    private final int code;
    private final String label;
    private final String tableName;
    private final String statusColumn;

    AuditBizType(int code, String label, String tableName, String statusColumn) {
        this.code = code;
        this.label = label;
        this.tableName = tableName;
        this.statusColumn = statusColumn;
    }

    public static AuditBizType of(int code) {
        for (AuditBizType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知可审对象类型: " + code);
    }
}
