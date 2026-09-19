package com.hongmai.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 资源状态机测试（承接 spec AC22「审核状态可追溯」与 plan §5.2 的合法迁移表）。
 * 非法迁移必须被拒绝并返回 4004，不能静默改写状态。
 */
class ResourceStatusTest {

    @Test
    @DisplayName("待审核可通过为已上架或退回草稿")
    void pendingTransitions() {
        assertTrue(ResourceStatus.PENDING.canTransitionTo(ResourceStatus.PUBLISHED));
        assertTrue(ResourceStatus.PENDING.canTransitionTo(ResourceStatus.DRAFT));
        assertFalse(ResourceStatus.PENDING.canTransitionTo(ResourceStatus.OFFLINE));
    }

    @Test
    @DisplayName("已上架只能下架")
    void publishedTransitions() {
        assertTrue(ResourceStatus.PUBLISHED.canTransitionTo(ResourceStatus.OFFLINE));
        assertFalse(ResourceStatus.PUBLISHED.canTransitionTo(ResourceStatus.DRAFT));
        assertFalse(ResourceStatus.PUBLISHED.canTransitionTo(ResourceStatus.PENDING));
    }

    @Test
    @DisplayName("草稿与已下架均为终态，不能再迁移")
    void terminalStates() {
        for (ResourceStatus target : ResourceStatus.values()) {
            assertFalse(ResourceStatus.DRAFT.canTransitionTo(target));
            assertFalse(ResourceStatus.OFFLINE.canTransitionTo(target));
        }
    }

    @Test
    @DisplayName("状态码往返映射一致")
    void codeRoundTrip() {
        for (ResourceStatus status : ResourceStatus.values()) {
            assertTrue(status == ResourceStatus.of(status.getCode()));
        }
    }
}
