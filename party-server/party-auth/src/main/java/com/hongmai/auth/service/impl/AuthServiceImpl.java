package com.hongmai.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongmai.audit.service.AuditService;
import com.hongmai.auth.client.WechatMiniappClient;
import com.hongmai.auth.domain.User;
import com.hongmai.auth.domain.UserRole;
import com.hongmai.auth.dto.RealNameCmd;
import com.hongmai.auth.mapper.UserMapper;
import com.hongmai.auth.mapper.UserRoleMapper;
import com.hongmai.auth.service.AuthService;
import com.hongmai.auth.vo.EntryVO;
import com.hongmai.auth.vo.LoginResult;
import com.hongmai.auth.vo.RealNameApplyVO;
import com.hongmai.common.auth.TokenPayload;
import com.hongmai.common.crypto.CryptoUtil;
import com.hongmai.common.crypto.MaskUtil;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.enums.AuditStatus;
import com.hongmai.common.enums.EntryType;
import com.hongmai.common.enums.IdentityType;
import com.hongmai.common.enums.RoleType;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.web.LoginContext;
import com.hongmai.common.web.PageQuery;
import com.hongmai.common.web.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String SCENE_REAL_NAME = "REAL_NAME";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final WechatMiniappClient wechatClient;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResult loginByWechat(String jsCode) {
        return loginByOpenId(wechatClient.code2Session(jsCode));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResult loginByOpenId(String openId) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenId, openId));
        boolean firstTime = false;
        if (user == null) {
            user = createNewUser(openId);
            firstTime = true;
        }

        Set<Integer> roles = new LinkedHashSet<>(userRoleMapper.selectRolesByUserId(user.getId()));
        List<EntryVO> entries = resolveEntries(roles);

        String token = UUID.randomUUID().toString().replace("-", "");
        boolean leader = user.getLeaderFlag() != null && user.getLeaderFlag() == 1;
        TokenPayload payload = new TokenPayload(user.getId(), roles,
                user.getOrgId() == null ? 0L : user.getOrgId(), leader);
        writeToken(token, payload);

        LoginResult result = new LoginResult();
        result.setToken(token);
        result.setRealNameStatus(user.getAuditStatus() == null
                ? AuditStatus.NOT_SUBMITTED.getCode() : user.getAuditStatus());
        result.setEntries(entries);
        result.setAvatarUrl(user.getAvatarUrl());
        result.setFirstTime(firstTime);
        return result;
    }

    private User createNewUser(String openId) {
        User user = new User();
        user.setOpenId(openId);
        // 建档即「未提交实名」，而不是「待审核」——两者混用会让前端反复引导已提交的用户去填表
        user.setAuditStatus(AuditStatus.NOT_SUBMITTED.getCode());
        user.setLeaderFlag(0);
        user.setStatus(1);
        userMapper.insert(user);

        UserRole role = new UserRole();
        role.setUserId(user.getId());
        role.setRole(RoleType.USER.getCode());
        userRoleMapper.insert(role);
        return user;
    }

    private List<EntryVO> resolveEntries(Set<Integer> roles) {
        List<EntryVO> entries = new ArrayList<>();
        entries.add(new EntryVO(EntryType.USER.getCode(), EntryType.USER.getLabel()));
        boolean canAdmin = roles.stream().anyMatch(r -> isAdminRole(r));
        if (canAdmin) {
            entries.add(new EntryVO(EntryType.ADMIN.getCode(), EntryType.ADMIN.getLabel()));
        }
        return entries;
    }

    private boolean isAdminRole(int roleCode) {
        for (RoleType role : RoleType.values()) {
            if (role.getCode() == roleCode) {
                return role.canEnterAdminEntry();
            }
        }
        return false;
    }

    /** 登录态只存 userId 与 roles：不放手机号等敏感信息，避免 Redis 成为第二个泄露面。 */
    private void writeToken(String token, TokenPayload payload) {
        try {
            redis.opsForValue().set(TokenPayload.redisKey(token),
                    objectMapper.writeValueAsString(payload),
                    Duration.ofSeconds(TokenPayload.TOKEN_TTL_SECONDS));
        } catch (Exception e) {
            log.error("登录态写入 Redis 失败 userId={}", payload.userId(), e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "登录态写入失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitRealName(long userId, RealNameCmd cmd) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        String mobileHash = CryptoUtil.deterministicHash(cmd.getMobile());
        // 用确定性哈希查重：密文带随机 IV，等值查询查不到重复
        if (userMapper.countByMobileHash(mobileHash, userId) > 0) {
            throw new BizException(ErrorCode.MOBILE_OCCUPIED);
        }

        User update = new User();
        update.setId(userId);
        update.setRealName(cmd.getRealName());
        update.setMobile(cmd.getMobile());
        update.setMobileHash(mobileHash);
        update.setMobileTail(MaskUtil.tail4(cmd.getMobile()));
        update.setOrgId(cmd.getOrgId());
        update.setIdentityType(cmd.getIdentityType());
        update.setEthnicity(cmd.getEthnicity());
        update.setAvatarUrl(cmd.getAvatarUrl());
        update.setLeaderFlag(Boolean.TRUE.equals(cmd.getLeader()) ? 1 : 0);
        update.setAuditStatus(AuditStatus.PENDING.getCode());
        update.setAuditRemark(null);
        userMapper.updateById(update);

        auditService.submit(cmd.getOrgId(), AuditBizType.REAL_NAME, userId,
                buildRealNameSummary(cmd), userId);
    }

    private String buildRealNameSummary(RealNameCmd cmd) {
        String identity = identityLabel(cmd.getIdentityType());
        return cmd.getRealName() + "（" + identity + "，手机尾号 "
                + MaskUtil.tail4(cmd.getMobile()) + "）申请实名认证";
    }

    private String identityLabel(Integer identityType) {
        if (identityType == null) {
            return "未知身份";
        }
        try {
            return IdentityType.of(identityType).getLabel();
        } catch (IllegalArgumentException e) {
            return "未知身份";
        }
    }

    @Override
    public PageResult<RealNameApplyVO> pageRealNameApply(long operatorId, PageQuery query) {
        List<Long> visible = LoginContext.currentVisibleOrgIds();
        List<User> rows = userMapper.selectPendingRealName(visible, query.getOffset(), query.getPageSize());
        long total = userMapper.countPendingRealName(visible);

        List<RealNameApplyVO> records = rows.stream().map(this::toVO).toList();
        return PageResult.of(query.getPageNo(), query.getPageSize(), total, records);
    }

    private RealNameApplyVO toVO(User user) {
        RealNameApplyVO vo = new RealNameApplyVO();
        vo.setUserId(user.getId());
        vo.setRealName(user.getRealName());
        vo.setMobileTail(user.getMobileTail());
        vo.setOrgId(user.getOrgId());
        vo.setIdentityType(user.getIdentityType());
        vo.setIdentityTypeLabel(identityLabel(user.getIdentityType()));
        vo.setEthnicity(user.getEthnicity());
        vo.setSubmittedAt(user.getUpdatedAt());
        return vo;
    }

    @Override
    public void reviewRealName(long operatorId, long targetUserId, boolean pass, String remark) {
        User user = userMapper.selectById(targetUserId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        // 委托 AuditService，保证 t_audit_log 的写入路径唯一
        auditService.review(operatorId, AuditBizType.REAL_NAME, targetUserId, pass, remark);
    }

    @Override
    public void assertEntryAllowed(long userId, EntryType entry) {
        // 用户端不校验角色，先返回再查库——否则每次切入口都白查一次角色表
        if (entry == EntryType.USER) {
            return;
        }
        List<Integer> roleCodes = userRoleMapper.selectRolesByUserId(userId);
        boolean allowed = roleCodes.stream().anyMatch(this::isAdminRole);
        if (!allowed) {
            throw new BizException(ErrorCode.ENTRY_NOT_ALLOWED);
        }
    }

    /** 供测试与排障：某用户的角色码列表。 */
    List<Integer> rolesOf(long userId) {
        return userRoleMapper.selectRolesByUserId(userId);
    }

    /** 场景常量，供敏感访问日志复用。 */
    public static String sceneRealName() {
        return SCENE_REAL_NAME;
    }

    static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
