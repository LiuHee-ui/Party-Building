package com.hongmai.common.enums;

import lombok.Getter;

/** 资源状态机：草稿 → 待审核 → 已上架 → 已下架。每次迁移必须落审核流水。 */
@Getter
public enum ResourceStatus {

    DRAFT(0, "草稿"),
    PENDING(1, "待审核"),
    PUBLISHED(2, "已上架"),
    OFFLINE(3, "已下架");

    private final int code;
    private final String label;

    ResourceStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ResourceStatus of(int code) {
        for (ResourceStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知资源状态: " + code);
    }

    /**
     * 合法迁移（plan §5.2 的 AuditBizType.RESOURCE 行）：
     * 待审核 → 已上架 / 草稿；已上架 → 已下架。
     */
    public boolean canTransitionTo(ResourceStatus target) {
        return switch (this) {
            case PENDING -> target == PUBLISHED || target == DRAFT;
            case PUBLISHED -> target == OFFLINE;
            default -> false;
        };
    }
}
