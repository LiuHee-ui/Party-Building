package com.hongmai.common.util;

import com.hongmai.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组织祖先路径测试（承接 spec F4 / AC4 数据范围）。
 * 前缀匹配是「本组织及下辖」的唯一实现方式，必须严格。
 */
class OrgPathUtilTest {

    @Test
    @DisplayName("根组织路径为 /{id}/")
    void buildRootPath() {
        assertEquals("/1/", OrgPathUtil.buildPath(null, 1L));
    }

    @Test
    @DisplayName("由上级路径拼接出子树路径")
    void buildChildPath() {
        assertEquals("/1/12/", OrgPathUtil.buildPath("/1/", 12L));
        assertEquals("/1/12/35/", OrgPathUtil.buildPath("/1/12/", 35L));
    }

    @Test
    @DisplayName("非法组织 id 被拒绝")
    void rejectIllegalOrgId() {
        assertThrows(BizException.class, () -> OrgPathUtil.buildPath("/1/", 0L));
    }

    @Test
    @DisplayName("归一化补全首尾斜杠并压缩重复斜杠")
    void normalize() {
        assertEquals("/", OrgPathUtil.normalize(null));
        assertEquals("/", OrgPathUtil.normalize(""));
        assertEquals("/1/", OrgPathUtil.normalize("1"));
        assertEquals("/1/12/", OrgPathUtil.normalize("/1/12"));
        assertEquals("/1/12/", OrgPathUtil.normalize("//1//12//"));
    }

    @Test
    @DisplayName("子节点被判定落在祖先子树内（含自身）")
    void descendantMatch() {
        assertTrue(OrgPathUtil.isDescendantOf("/1/12/35/", "/1/12/"));
        assertTrue(OrgPathUtil.isDescendantOf("/1/12/", "/1/12/"));
        assertTrue(OrgPathUtil.isDescendantOf("/1/12/35/", "/"));
    }

    @Test
    @DisplayName("非子树节点不被误判")
    void nonDescendantRejected() {
        assertFalse(OrgPathUtil.isDescendantOf("/1/13/", "/1/12/"));
        assertFalse(OrgPathUtil.isDescendantOf("/2/12/", "/1/"));
        assertFalse(OrgPathUtil.isDescendantOf(null, "/1/"));
        assertFalse(OrgPathUtil.isDescendantOf("/1/12/", null));
    }

    @Test
    @DisplayName("前缀匹配不会把 /1/120/ 误判为 /1/12/ 的下级")
    void prefixMatchIsSegmentAware() {
        // 归一化后带尾斜杠，/1/120/ 不以 /1/12/ 为前缀，因此不会误判
        assertFalse(OrgPathUtil.isDescendantOf("/1/120/", "/1/12/"));
    }

    @Test
    @DisplayName("解析路径上的全部组织 id")
    void extractIds() {
        assertEquals(List.of(1L, 12L, 35L), OrgPathUtil.extractIds("/1/12/35/"));
        assertEquals(List.of(1L), OrgPathUtil.extractIds("/1/"));
    }

    @Test
    @DisplayName("取直属上级组织 id，根节点返回 0")
    void parentIdOf() {
        assertEquals(12L, OrgPathUtil.parentIdOf("/1/12/35/"));
        assertEquals(0L, OrgPathUtil.parentIdOf("/1/"));
    }
}
