package com.hongmai.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 台账与看板口径测试（承接 spec F18/F19 与 plan §5.2 的 DashboardVO 口径说明）。
 * 在册人数只含党员与积极分子，群众与学生不计入——否则看板与台账对不上，AC20 不成立。
 */
class IdentityTypeTest {

    @Test
    @DisplayName("党员与积极分子计入台账")
    void rosterIncludesMembersAndActivists() {
        assertTrue(IdentityType.FULL_MEMBER.countsInRoster());
        assertTrue(IdentityType.PROBATIONARY_MEMBER.countsInRoster());
        assertTrue(IdentityType.ACTIVIST.countsInRoster());
    }

    @Test
    @DisplayName("群众与学生不计入台账")
    void rosterExcludesMassAndStudent() {
        assertFalse(IdentityType.MASS.countsInRoster());
        assertFalse(IdentityType.STUDENT.countsInRoster());
    }

    @Test
    @DisplayName("角色权限：仅管理类角色可进管理端")
    void adminEntryRoles() {
        assertTrue(RoleType.ORG_ADMIN.canEnterAdminEntry());
        assertTrue(RoleType.SUPERIOR_ORG_ADMIN.canEnterAdminEntry());
        assertTrue(RoleType.PLATFORM_ADMIN.canEnterAdminEntry());
        assertFalse(RoleType.USER.canEnterAdminEntry());
        assertFalse(RoleType.ACTIVIST.canEnterAdminEntry());
    }

    @Test
    @DisplayName("角色权限：仅平台管理员绕过数据范围")
    void dataScopeBypass() {
        assertTrue(RoleType.PLATFORM_ADMIN.bypassDataScope());
        assertFalse(RoleType.ORG_ADMIN.bypassDataScope());
        assertFalse(RoleType.SUPERIOR_ORG_ADMIN.bypassDataScope());
    }
}
