package com.hongmai.common.spi;

/**
 * 组织层级查询能力（跨模块 SPI）。
 *
 * 为什么放在 common 而不是直接依赖 party-org：
 * party-org 依赖 party-auth，若 party-auth 再依赖 party-org 就成环了。
 * 因此把「查组织层级」这个能力抽成接口放在 common（双方共同的下层），
 * 由 party-org 实现，party-auth 按需注入。
 *
 * 消费方须用 ObjectProvider 注入并容忍实现缺失——这样 auth 模块在 org 模块
 * 尚未就位时也能独立编译与运行。
 */
public interface OrgLevelProvider {

    /**
     * 取组织层级：1 党委 / 2 党总支 / 3 党支部。
     * 组织不存在或已停用时返回 0。
     */
    int levelOf(long orgId);
}
