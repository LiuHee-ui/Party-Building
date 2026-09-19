package com.hongmai.learn.service.impl;

import com.hongmai.common.enums.PeriodType;
import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.learn.domain.CreditTarget;
import com.hongmai.learn.domain.CreditTargetRule;
import com.hongmai.learn.mapper.CreditTargetMapper;
import com.hongmai.learn.service.CreditTargetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditTargetServiceImpl implements CreditTargetService {

    private final CreditTargetMapper creditTargetMapper;

    @Override
    public BigDecimal resolveTarget(long userId, boolean leader, PeriodType periodType, String periodKey) {
        CreditTarget customized = creditTargetMapper.selectByUserPeriod(
                userId, periodType.getCode(), periodKey);
        if (customized != null && customized.getTargetCredit() != null
                && customized.getTargetCredit().compareTo(BigDecimal.ZERO) > 0) {
            return customized.getTargetCredit();
        }
        return CreditTargetRule.defaultTarget(leader, periodType);
    }

    @Override
    public boolean isCustomized(long userId, PeriodType periodType, String periodKey) {
        CreditTarget customized = creditTargetMapper.selectByUserPeriod(
                userId, periodType.getCode(), periodKey);
        return customized != null && customized.getTargetCredit() != null
                && customized.getTargetCredit().compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setTarget(long operatorId, long userId, PeriodType periodType, String periodKey,
                          BigDecimal target) {
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.CREDIT_TARGET_INVALID);
        }

        CreditTarget existing = creditTargetMapper.selectByUserPeriod(
                userId, periodType.getCode(), periodKey);
        if (existing == null) {
            CreditTarget entity = new CreditTarget();
            entity.setUserId(userId);
            entity.setPeriodType(periodType.getCode());
            entity.setPeriodKey(periodKey);
            entity.setTargetCredit(target);
            entity.setSetBy(operatorId);
            entity.setSetAt(com.hongmai.common.util.DateUtil.now());
            creditTargetMapper.insert(entity);
        } else {
            CreditTarget update = new CreditTarget();
            update.setId(existing.getId());
            update.setTargetCredit(target);
            update.setSetBy(operatorId);
            update.setSetAt(com.hongmai.common.util.DateUtil.now());
            creditTargetMapper.updateById(update);
        }
        log.info("目标学时已设置 userId={} period={} target={}", userId, periodKey, target);
    }
}
