package com.hongmai.auth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongmai.audit.service.AuditService;
import com.hongmai.auth.client.WechatMiniappClient;
import com.hongmai.auth.domain.User;
import com.hongmai.auth.dto.RealNameCmd;
import com.hongmai.auth.mapper.UserMapper;
import com.hongmai.auth.mapper.UserRoleMapper;
import com.hongmai.auth.vo.LoginResult;
import com.hongmai.auth.vo.RealNameApplyVO;
import com.hongmai.common.crypto.CryptoUtil;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.enums.AuditStatus;
import com.hongmai.common.enums.EntryType;
import com.hongmai.common.enums.RoleType;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.web.LoginContext;
import com.hongmai.common.web.PageQuery;
import com.hongmai.common.web.PageResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务测试。
 * 重点验证：首次建档是「未提交」而不是「待审核」、手机号查重用确定性哈希、
 * 实名提交必须落审核流水、入口鉴权按角色判定。
 */
class AuthServiceImplTest {

    private UserMapper userMapper;
    private UserRoleMapper userRoleMapper;
    private WechatMiniappClient wechatClient;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private AuditService auditService;
    private AuthServiceImpl service;

    @BeforeAll
    static void initCrypto() {
        CryptoUtil.initKey(Base64.getEncoder()
                .encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        wechatClient = mock(WechatMiniappClient.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        auditService = mock(AuditService.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        service = new AuthServiceImpl(userMapper, userRoleMapper, wechatClient,
                redis, new ObjectMapper(), auditService);
    }

    // ---------- 登录 ----------

    @Test
    @DisplayName("首次登录建档，实名状态为「未提交」而非「待审核」")
    void firstLoginCreatesUserAsNotSubmitted() {
        when(wechatClient.code2Session("code-1")).thenReturn("openid-1");
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(100L);
            return 1;
        });
        when(userRoleMapper.insert(any())).thenReturn(1);
        when(userRoleMapper.selectRolesByUserId(anyLong())).thenReturn(List.of(RoleType.USER.getCode()));

        LoginResult result = service.loginByWechat("code-1");

        assertEquals(AuditStatus.NOT_SUBMITTED.getCode(), result.getRealNameStatus());
        assertTrue(result.getFirstTime());
        assertNotNull(result.getToken());

        // 建档时写入的用户状态必须是「未提交」——写成「待审核」会误导管理端出现假待办
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals(AuditStatus.NOT_SUBMITTED.getCode(), captor.getValue().getAuditStatus());
    }

    @Test
    @DisplayName("普通用户只能进入用户端")
    void normalUserOnlyUserEntry() {
        when(wechatClient.code2Session("code-2")).thenReturn("openid-2");
        User existing = user(200L, AuditStatus.APPROVED.getCode());
        when(userMapper.selectOne(any())).thenReturn(existing);
        when(userRoleMapper.selectRolesByUserId(200L)).thenReturn(List.of(RoleType.USER.getCode()));

        LoginResult result = service.loginByWechat("code-2");

        assertEquals(1, result.getEntries().size());
        assertEquals(EntryType.USER.getCode(), result.getEntries().get(0).getCode());
        assertFalse(result.getFirstTime());
    }

    @Test
    @DisplayName("管理员账号可进入两个入口")
    void adminHasBothEntries() {
        when(wechatClient.code2Session("code-3")).thenReturn("openid-3");
        when(userMapper.selectOne(any())).thenReturn(user(300L, AuditStatus.APPROVED.getCode()));
        when(userRoleMapper.selectRolesByUserId(300L))
                .thenReturn(List.of(RoleType.USER.getCode(), RoleType.ORG_ADMIN.getCode()));

        LoginResult result = service.loginByWechat("code-3");

        assertEquals(2, result.getEntries().size());
        assertTrue(result.getEntries().stream().anyMatch(e -> EntryType.ADMIN.getCode().equals(e.getCode())));
    }

    @Test
    @DisplayName("登录态写入 Redis 并设 7 天 TTL")
    void tokenWrittenWithTtl() {
        when(wechatClient.code2Session("code-4")).thenReturn("openid-4");
        when(userMapper.selectOne(any())).thenReturn(user(400L, AuditStatus.APPROVED.getCode()));
        when(userRoleMapper.selectRolesByUserId(400L)).thenReturn(List.of());

        service.loginByWechat("code-4");

        verify(valueOps).set(anyString(), anyString(),
                eq(Duration.ofSeconds(com.hongmai.common.auth.TokenPayload.TOKEN_TTL_SECONDS)));
    }

    // ---------- 实名提交 ----------

    @Test
    @DisplayName("手机号已被占用时拒绝，且不落审核流水")
    void duplicateMobileRejected() {
        when(userMapper.selectById(500L)).thenReturn(user(500L, AuditStatus.NOT_SUBMITTED.getCode()));
        when(userMapper.countByMobileHash(anyString(), eq(500L))).thenReturn(1);

        BizException ex = assertThrows(BizException.class, () -> service.submitRealName(500L, realNameCmd()));

        assertEquals(ErrorCode.MOBILE_OCCUPIED.getCode(), ex.getCode());
        verify(userMapper, never()).updateById(any(User.class));
        verify(auditService, never()).submit(anyLong(), any(), anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("提交实名：状态置待审核、存哈希与末四位、落审核流水")
    void submitRealNameHappyPath() {
        when(userMapper.selectById(500L)).thenReturn(user(500L, AuditStatus.NOT_SUBMITTED.getCode()));
        when(userMapper.countByMobileHash(anyString(), eq(500L))).thenReturn(0);

        service.submitRealName(500L, realNameCmd());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        User updated = captor.getValue();
        assertEquals(AuditStatus.PENDING.getCode(), updated.getAuditStatus());
        assertEquals("8000", updated.getMobileTail());
        // 哈希必须是确定性的：密文带随机 IV，用它做查重会失效
        assertEquals(CryptoUtil.deterministicHash("13800138000"), updated.getMobileHash());

        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(auditService).submit(eq(3L), eq(AuditBizType.REAL_NAME), eq(500L),
                summary.capture(), eq(500L));
        assertTrue(summary.getValue().contains("张"));
        assertTrue(summary.getValue().contains("8000"));
        // 完整手机号不得出现在摘要里，摘要会展示在待审队列
        assertFalse(summary.getValue().contains("13800138000"));
    }

    @Test
    @DisplayName("实名提交时重算哈希排除自身，避免同一用户重提交被判重复")
    void mobileCheckExcludesSelf() {
        when(userMapper.selectById(500L)).thenReturn(user(500L, AuditStatus.REJECTED.getCode()));
        when(userMapper.countByMobileHash(anyString(), eq(500L))).thenReturn(0);

        service.submitRealName(500L, realNameCmd());

        verify(userMapper).countByMobileHash(anyString(), eq(500L));
    }

    @Test
    @DisplayName("用户不存在时报 4008")
    void submitRealNameUserMissing() {
        when(userMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.submitRealName(999L, realNameCmd()));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    // ---------- 入口鉴权 ----------

    @Test
    @DisplayName("无管理角色进入管理端报 3002")
    void entryDeniedForNormalUser() {
        when(userRoleMapper.selectRolesByUserId(600L)).thenReturn(List.of(RoleType.USER.getCode()));

        BizException ex = assertThrows(BizException.class,
                () -> service.assertEntryAllowed(600L, EntryType.ADMIN));

        assertEquals(ErrorCode.ENTRY_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("三种管理角色都可进入管理端")
    void entryAllowedForAdminRoles() {
        for (RoleType role : List.of(RoleType.ORG_ADMIN, RoleType.SUPERIOR_ORG_ADMIN, RoleType.PLATFORM_ADMIN)) {
            when(userRoleMapper.selectRolesByUserId(700L)).thenReturn(List.of(role.getCode()));
            service.assertEntryAllowed(700L, EntryType.ADMIN);
        }
        verify(userRoleMapper, times(3)).selectRolesByUserId(700L);
    }

    @Test
    @DisplayName("用户端入口不校验角色")
    void userEntryNeedsNoRole() {
        service.assertEntryAllowed(800L, EntryType.USER);
        verify(userRoleMapper, never()).selectRolesByUserId(anyLong());
    }

    // ---------- 待审列表数据范围 ----------

    @Test
    @DisplayName("可见组织为空集时不得退化为查全部（越权防护）")
    void emptyVisibleOrgsReturnsNothing() {
        LoginContext context = new LoginContext();
        context.setUserId(900L);
        context.setVisibleOrgIds(List.of());
        LoginContext.set(context);
        try {
            when(userMapper.selectPendingRealName(eq(List.of()), anyInt(), anyInt())).thenReturn(List.of());
            when(userMapper.countPendingRealName(eq(List.of()))).thenReturn(0L);

            PageResult<RealNameApplyVO> page = service.pageRealNameApply(900L, new PageQuery());

            assertEquals(0, page.getTotal());
            verify(userMapper).selectPendingRealName(eq(List.of()), anyInt(), anyInt());
        } finally {
            LoginContext.clear();
        }
    }

    // ---------- 辅助 ----------

    private User user(long id, int auditStatus) {
        User u = new User();
        u.setId(id);
        u.setOpenId("openid-" + id);
        u.setAuditStatus(auditStatus);
        u.setOrgId(3L);
        u.setLeaderFlag(0);
        return u;
    }

    private RealNameCmd realNameCmd() {
        RealNameCmd cmd = new RealNameCmd();
        cmd.setRealName("张三");
        cmd.setMobile("13800138000");
        cmd.setOrgId(3L);
        cmd.setIdentityType(3);
        cmd.setEthnicity("藏族");
        cmd.setLeader(false);
        return cmd;
    }
}
