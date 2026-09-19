package com.hongmai.auth.service.impl;

import com.hongmai.audit.domain.AuditableHandler;
import com.hongmai.auth.domain.AdminApplication;
import com.hongmai.auth.mapper.AdminApplicationMapper;
import com.hongmai.auth.mapper.UserRoleMapper;
import com.hongmai.auth.domain.UserRole;
import com.hongmai.common.enums.AuditAction;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.enums.AuditStatus;
import com.hongmai.common.enums.RoleType;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.spi.OrgLevelProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 管理员申请审核处理器。
 *
 * 通过时除了迁移状态，还要授予管理类角色。授予哪一类取决于组织层级：
 * 党委（level=1）授予上级组织管理员，党总支/党支部授予组织管理员。
 *
 * 组织层级由 OrgLevelProvider 提供——接口在 common，实现在 party-org。
 * 用 ObjectProvider 注入并容忍实现缺失：auth 模块在 org 模块就位前也能独立运行，
 * 此时降级为组织管理员。这是打破循环依赖的标准做法。
 */
@Slf4j
@Component
public class AdminApplyAuditHandler implements AuditableHandler {

    private final AdminApplicationMapper applicationMapper;
    private final UserRoleMapper userRoleMapper;
    private final ObjectProvider<OrgLevelProvider> orgLevelProvider;

    public AdminApplyAuditHandler(AdminApplicationMapper applicationMapper,
                                  UserRoleMapper userRoleMapper,
                                  ObjectProvider<OrgLevelProvider> orgLevelProvider) {
        this.applicationMapper = applicationMapper;
        this.userRoleMapper = userRoleMapper;
        this.orgLevelProvider = orgLevelProvider;
    }

    @Override
    public AuditBizType bizType() {
        return AuditBizType.ADMIN_APPLY;
    }

    @Override
    public void migrate(long bizId, AuditAction action, String remark, long operatorId) {
        AdminApplication application = applicationMapper.selectById(bizId);
        if (application == null) {
            throw new BizException(ErrorCode.AUDIT_ILLEGAL_TRANSITION, "管理员申请不存在");
        }

        int toStatus = switch (action) {
            case APPROVE -> AuditStatus.APPROVED.getCode();
            case REJECT -> AuditStatus.REJECTED.getCode();
            default -> throw new BizException(ErrorCode.AUDIT_ILLEGAL_TRANSITION,
                    "管理员申请审核不支持动作 " + action.getCode());
        };

        int rows = applicationMapper.migrateStatus(bizId, toStatus, operatorId, remark);
        if (rows == 0) {
            throw new BizException(ErrorCode.AUDIT_ILLEGAL_TRANSITION,
                    "该申请不处于待审核状态，或已被处理");
        }

        if (action == AuditAction.APPROVE) {
            grantAdminRole(application);
        }
    }

    private void grantAdminRole(AdminApplication application) {
        RoleType role = resolveRole(application.getOrgId());
        // 幂等：uk_user_role 已保证不重复，但先查一次能避免依赖唯一键异常做流程控制
        if (userRoleMapper.countByUserAndRole(application.getUserId(), role.getCode()) > 0) {
            return;
        }
        UserRole entity = new UserRole();
        entity.setUserId(application.getUserId());
        entity.setRole(role.getCode());
        userRoleMapper.insert(entity);
        log.info("管理员申请通过，授予角色 userId={} role={}", application.getUserId(), role.getLabel());
    }

    /** 组织层级缺失时降级为组织管理员，而不是授予更高权限。 */
    private RoleType resolveRole(long orgId) {
        OrgLevelProvider provider = orgLevelProvider.getIfAvailable();
        if (provider == null) {
            return RoleType.ORG_ADMIN;
        }
        int level = provider.levelOf(orgId);
        return level == 1 ? RoleType.SUPERIOR_ORG_ADMIN : RoleType.ORG_ADMIN;
    }
}
