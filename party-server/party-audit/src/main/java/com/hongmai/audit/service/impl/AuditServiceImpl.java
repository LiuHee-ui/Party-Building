package com.hongmai.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongmai.audit.domain.AuditLog;
import com.hongmai.audit.domain.AuditableHandler;
import com.hongmai.audit.mapper.AuditLogMapper;
import com.hongmai.audit.service.AuditService;
import com.hongmai.audit.vo.AuditTaskVO;
import com.hongmai.common.enums.AuditAction;
import com.hongmai.common.enums.AuditBizType;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.web.LoginContext;
import com.hongmai.common.web.PageQuery;
import com.hongmai.common.web.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;

    /** 按可审类型索引的处理器注册表。 */
    private final Map<AuditBizType, AuditableHandler> handlerRegistry;

    public AuditServiceImpl(AuditLogMapper auditLogMapper, List<AuditableHandler> handlers) {
        this.auditLogMapper = auditLogMapper;
        this.handlerRegistry = new EnumMap<>(AuditBizType.class);
        for (AuditableHandler handler : handlers) {
            AuditableHandler previous = handlerRegistry.put(handler.bizType(), handler);
            if (previous != null) {
                // 同一类型注册了两个处理器时，审核会随机走其中一个 —— 必须在启动期发现
                throw new IllegalStateException("可审类型 " + handler.bizType()
                        + " 注册了多个处理器：" + previous.getClass().getName()
                        + " 与 " + handler.getClass().getName());
            }
        }
        log.info("审核处理器注册完成，共 {} 个：{}", handlerRegistry.size(), handlerRegistry.keySet());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void record(long orgId, AuditBizType bizType, long bizId, String summary,
                       long operatorId, AuditAction action) {
        AuditLog entity = new AuditLog();
        entity.setBizType(bizType.getCode());
        entity.setBizId(bizId);
        entity.setOrgId(orgId);
        entity.setSummary(truncate(summary, 200));
        entity.setAction(action.getCode());
        entity.setPending(action.isPending() ? 1 : 0);
        entity.setOperatorId(operatorId);
        auditLogMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(long operatorId, AuditBizType bizType, long bizId, boolean pass, String remark) {
        if (!pass && !StringUtils.hasText(remark)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "驳回时必须填写审核意见");
        }

        AuditableHandler handler = handlerRegistry.get(bizType);
        if (handler == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "未注册可审类型 " + bizType.getLabel() + " 的处理器");
        }

        // 先让业务模块迁移状态。它自己校验合法迁移，非法时抛 4004，事务回滚，流水也不会写
        AuditAction action = pass ? AuditAction.APPROVE : AuditAction.REJECT;
        handler.migrate(bizId, action, remark, operatorId);

        int cleared = auditLogMapper.clearPending(bizType.getCode(), bizId);
        if (cleared == 0) {
            // 状态迁移成功但找不到待审任务，说明数据不一致，直接回滚暴露问题而不是静默吞掉
            throw new BizException(ErrorCode.AUDIT_ILLEGAL_TRANSITION, "该对象没有待审核任务");
        }

        AuditLog entity = new AuditLog();
        entity.setBizType(bizType.getCode());
        entity.setBizId(bizId);
        entity.setOrgId(currentOrgIdOrZero());
        entity.setAction(action.getCode());
        entity.setPending(0);
        entity.setOperatorId(operatorId);
        entity.setRemark(remark);
        auditLogMapper.insert(entity);
    }

    @Override
    public PageResult<AuditTaskVO> pagePending(long operatorId, AuditBizType bizType, PageQuery query) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getPending, 1)
                .eq(bizType != null, AuditLog::getBizType, bizType == null ? null : bizType.getCode())
                .orderByDesc(AuditLog::getCreatedAt);

        applyDataScope(wrapper);

        Page<AuditLog> page = auditLogMapper.selectPage(
                Page.of(query.getPageNo(), query.getPageSize()), wrapper);

        List<AuditTaskVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(query.getPageNo(), query.getPageSize(), page.getTotal(), records);
    }

    /**
     * 数据范围：visibleOrgIds 为 null 表示不限（平台管理员），为空集表示无任何可见组织 —— 后者必须
     * 生成一个恒假条件，否则会退化成「查全部」，这是最典型的越权漏洞。
     */
    private void applyDataScope(LambdaQueryWrapper<AuditLog> wrapper) {
        List<Long> visible = LoginContext.currentVisibleOrgIds();
        if (visible == null) {
            return;
        }
        if (visible.isEmpty()) {
            wrapper.eq(AuditLog::getId, -1L);
            return;
        }
        wrapper.in(AuditLog::getOrgId, visible);
    }

    private AuditTaskVO toVO(AuditLog entity) {
        AuditTaskVO vo = new AuditTaskVO();
        vo.setLogId(entity.getId());
        vo.setBizType(entity.getBizType());
        vo.setBizTypeLabel(AuditBizType.of(entity.getBizType()).getLabel());
        vo.setBizId(entity.getBizId());
        vo.setOrgId(entity.getOrgId());
        vo.setOperatorId(entity.getOperatorId());
        vo.setSummary(entity.getSummary());
        vo.setSubmittedAt(entity.getCreatedAt());
        return vo;
    }

    private long currentOrgIdOrZero() {
        LoginContext context = LoginContext.get();
        return context == null ? 0L : context.getPrimaryOrgId();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** 便于测试与排障：当前已注册的可审类型。 */
    public Set<AuditBizType> registeredTypes() {
        return handlerRegistry.keySet().stream().collect(Collectors.toUnmodifiableSet());
    }
}
