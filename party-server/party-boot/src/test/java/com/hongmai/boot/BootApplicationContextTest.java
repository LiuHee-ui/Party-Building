package com.hongmai.boot;

import com.hongmai.audit.domain.AuditableHandler;
import com.hongmai.audit.service.AuditService;
import com.hongmai.auth.service.AdminApplyService;
import com.hongmai.auth.service.AuthService;
import com.hongmai.auth.spi.UserRosterProvider;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.spi.OrgLevelProvider;
import com.hongmai.common.spi.RankingScoreProvider;
import com.hongmai.common.storage.FileStorageService;
import com.hongmai.content.service.ResourceService;
import com.hongmai.exam.service.DashboardService;
import com.hongmai.exam.service.ExamService;
import com.hongmai.exam.service.RankingService;
import com.hongmai.learn.service.CreditService;
import com.hongmai.learn.service.CreditTargetService;
import com.hongmai.org.service.OrgService;
import com.hongmai.org.service.RosterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 启动装配测试。
 *
 * 单测各自 mock 掉依赖，无法发现「Bean 装配不起来」这种问题——
 * 而本项目有两处依赖倒置（AuditableHandler、OrgLevelProvider），
 * 一旦实现类漏了 @Component 或接口签名不匹配，只有真实启动才能暴露。
 *
 * 注意：不连真实 Redis 与 MySQL。Spring Boot 的连接池是懒初始化的，
 * 上下文装配不会触发连接，因此本测试可以在没有外部依赖的机器上跑。
 *
 * crypto.key 在这里显式注入一个**测试专用固定密钥**（"0123456789abcdef" 的 Base64），
 * 与 CryptoUtilTest / AuthServiceImplTest 用的是同一个夹具值。
 * 原因：application-dev.yml 已经不再提供默认密钥（fail-fast，避免用公开密钥加密敏感字段），
 * 若这里不补，本测试在全新克隆的机器上会因 crypto.key 缺失而起不来。
 * 这个值是公开的测试夹具，不是任何环境在用的密钥。
 */
@SpringBootTest(properties = "crypto.key=MDEyMzQ1Njc4OWFiY2RlZg==")
@ActiveProfiles("dev")
class BootApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Spring 上下文可以完整装配")
    void contextLoads() {
        assertNotNull(context);
    }

    @Test
    @DisplayName("两处依赖倒置的实现都已注册")
    void dependencyInversionImplementationsRegistered() {
        // 第一次倒置：可审对象能力接口 —— 当前应有资源、实名、管理员申请三个实现
        List<AuditableHandler> handlers = context.getBeansOfType(AuditableHandler.class)
                .values().stream().toList();
        Set<AuditBizType> registeredTypes = handlers.stream()
                .map(AuditableHandler::bizType)
                .collect(Collectors.toSet());

        assertTrue(registeredTypes.contains(AuditBizType.RESOURCE),
                "资源审核处理器未注册，审核流程会直接报错");
        assertTrue(registeredTypes.contains(AuditBizType.REAL_NAME),
                "实名审核处理器未注册");
        assertTrue(registeredTypes.contains(AuditBizType.ADMIN_APPLY),
                "管理员申请审核处理器未注册");
        assertEquals(handlers.size(), registeredTypes.size(),
                "同一可审类型注册了多个处理器，审核会随机走其中一个");

        // 第二次倒置：组织层级能力接口 —— 必须由 party-org 提供实现，
        // 否则管理员申请通过后无法按组织层级授予正确的角色
        assertNotNull(context.getBean(OrgLevelProvider.class),
                "OrgLevelProvider 未注册，管理员授权会降级为组织管理员");

        // 第三次倒置：榜单写入能力接口 —— 由 party-exam 提供实现。
        // 缺失时学时照常入账，但榜单只能靠每日快照更新，用户端看不到实时名次变化
        assertNotNull(context.getBean(RankingScoreProvider.class),
                "RankingScoreProvider 未注册，学时榜单将只能靠每日快照更新");
    }

    @Test
    @DisplayName("party-exam 的判分与榜单服务已装配")
    void examServicesPresent() {
        assertNotNull(context.getBean(ExamService.class));
        assertNotNull(context.getBean(RankingService.class));
        assertNotNull(context.getBean(DashboardService.class));
        assertNotNull(context.getBean(CreditService.class));
        assertNotNull(context.getBean(CreditTargetService.class));
    }

    @Test
    @DisplayName("核心服务 Bean 齐备")
    void coreServicesPresent() {
        assertNotNull(context.getBean(AuditService.class));
        assertNotNull(context.getBean(AuthService.class));
        assertNotNull(context.getBean(AdminApplyService.class));
        assertNotNull(context.getBean(OrgService.class));
        assertNotNull(context.getBean(RosterService.class));
        assertNotNull(context.getBean(ResourceService.class));
        assertNotNull(context.getBean(UserRosterProvider.class));
    }

    @Test
    @DisplayName("文件存储实现已装配（本地磁盘）")
    void fileStoragePresent() {
        assertNotNull(context.getBean(FileStorageService.class));
    }
}
