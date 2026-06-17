package com.dataplatform.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.billing.entity.TenantBudget;
import com.dataplatform.billing.mapper.TenantBudgetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 计费域计费计算的 Budget Alert Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class BudgetAlertService extends ServiceImpl<TenantBudgetMapper, TenantBudget> {

    private static final Logger log = LoggerFactory.getLogger(BudgetAlertService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String BUDGET_KEY_PREFIX = "budget:alert:";

    @Transactional(rollbackFor = Exception.class)
    public boolean checkAndAlert(Long tenantId, BigDecimal newAmount) {
        LambdaUpdateWrapper<TenantBudget> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TenantBudget::getTenantId, tenantId)
            .setSql("used_amount = COALESCE(used_amount, 0) + " + newAmount.toString())
            .set(TenantBudget::getUpdatedAt, LocalDateTime.now());

        boolean updated = this.update(updateWrapper);
        if (!updated) {
            return false;
        }

        TenantBudget budget = getByTenantId(tenantId);
        if (budget == null || budget.getMonthlyBudget() == null) {
            return false;
        }

        BigDecimal usedAmount = budget.getUsedAmount() != null ? budget.getUsedAmount() : BigDecimal.ZERO;
        BigDecimal budgetAmount = budget.getMonthlyBudget();

        BigDecimal usageRate = usedAmount.divide(budgetAmount, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));

        int alertLevel = 0;
        if (budget.getWarningThreshold() != null) {
            BigDecimal warningThreshold = budget.getWarningThreshold();
            if (usageRate.compareTo(warningThreshold) >= 0) {
                alertLevel = 1;
                sendAlert(tenantId, "warning", usageRate, budgetAmount, usedAmount);
            }
        }

        if (budget.getLimitThreshold() != null) {
            BigDecimal limitThreshold = budget.getLimitThreshold();
            if (usageRate.compareTo(limitThreshold) >= 0) {
                alertLevel = 2;
                sendAlert(tenantId, "limit", usageRate, budgetAmount, usedAmount);
            }
        }

        if (alertLevel != budget.getAlertLevel()) {
            LambdaUpdateWrapper<TenantBudget> alertUpdateWrapper = new LambdaUpdateWrapper<>();
            alertUpdateWrapper.eq(TenantBudget::getTenantId, tenantId)
                .set(TenantBudget::getAlertLevel, alertLevel)
                .set(TenantBudget::getUpdatedAt, LocalDateTime.now());
            this.update(alertUpdateWrapper);
        }

        return alertLevel >= 2;
    }

    public boolean isOverLimit(Long tenantId) {
        TenantBudget budget = getByTenantId(tenantId);
        if (budget == null || budget.getMonthlyBudget() == null) {
            return false;
        }

        BigDecimal usedAmount = budget.getUsedAmount() != null ? budget.getUsedAmount() : BigDecimal.ZERO;
        BigDecimal budgetAmount = budget.getMonthlyBudget();
        BigDecimal limitThreshold = budget.getLimitThreshold() != null
            ? budget.getLimitThreshold()
            : new BigDecimal("100");

        BigDecimal usageRate = usedAmount.divide(budgetAmount, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));

        return usageRate.compareTo(limitThreshold) >= 0;
    }

    public TenantBudget getByTenantId(Long tenantId) {
        LambdaQueryWrapper<TenantBudget> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantBudget::getTenantId, tenantId);
        return this.getOne(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveBudget(TenantBudget budget) {
        TenantBudget existing = getByTenantId(budget.getTenantId());
        if (existing != null) {
            budget.setId(existing.getId());
            budget.setUpdatedAt(LocalDateTime.now());
            return this.updateById(budget);
        }
        budget.setUsedAmount(BigDecimal.ZERO);
        budget.setAlertLevel(0);
        budget.setStatus("active");
        budget.setCreatedAt(LocalDateTime.now());
        budget.setUpdatedAt(LocalDateTime.now());
        return this.save(budget);
    }

    public List<TenantBudget> getAllBudgets() {
        return this.list();
    }

    public List<TenantBudget> getWarningBudgets() {
        LambdaQueryWrapper<TenantBudget> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantBudget::getStatus, "active");
        wrapper.gt(TenantBudget::getAlertLevel, 0);
        return this.list(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetMonthlyBudget() {
        LambdaUpdateWrapper<TenantBudget> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TenantBudget::getStatus, "active")
            .set(TenantBudget::getUsedAmount, BigDecimal.ZERO)
            .set(TenantBudget::getAlertLevel, 0)
            .set(TenantBudget::getUpdatedAt, LocalDateTime.now());

        boolean updated = this.update(wrapper);
        if (updated) {
            log.info("月度预算已重置");
        }
    }

    private void sendAlert(Long tenantId, String alertType, BigDecimal usageRate,
                          BigDecimal budgetAmount, BigDecimal usedAmount) {
        String cacheKey = BUDGET_KEY_PREFIX + tenantId + ":" + alertType;

        String lastAlert = redisTemplate.opsForValue().get(cacheKey);
        if (lastAlert != null) {
            return;
        }

        String message = String.format("租户[%d]费用预警: 已使用%.1f%% (%.2f/%.2f元)",
            tenantId, usageRate, usedAmount, budgetAmount);

        log.warn("Budget Alert: {}", message);

        redisTemplate.opsForValue().set(cacheKey, message, 24, TimeUnit.HOURS);
    }

    public BigDecimal getUsageRate(Long tenantId) {
        TenantBudget budget = getByTenantId(tenantId);
        if (budget == null || budget.getMonthlyBudget() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal usedAmount = budget.getUsedAmount() != null ? budget.getUsedAmount() : BigDecimal.ZERO;
        return usedAmount.divide(budget.getMonthlyBudget(), 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }
}