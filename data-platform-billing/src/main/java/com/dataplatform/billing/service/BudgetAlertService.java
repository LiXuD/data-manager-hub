package com.dataplatform.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.billing.entity.TenantBudget;
import com.dataplatform.billing.mapper.TenantBudgetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class BudgetAlertService extends ServiceImpl<TenantBudgetMapper, TenantBudget> {

    private static final Logger log = LoggerFactory.getLogger(BudgetAlertService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String BUDGET_KEY_PREFIX = "budget:alert:";

    public boolean checkAndAlert(Long tenantId, BigDecimal newAmount) {
        TenantBudget budget = getByTenantId(tenantId);
        if (budget == null || budget.getMonthlyBudget() == null) {
            return false;
        }

        BigDecimal usedAmount = budget.getUsedAmount() != null ? budget.getUsedAmount() : BigDecimal.ZERO;
        BigDecimal totalAmount = usedAmount.add(newAmount);
        BigDecimal budgetAmount = budget.getMonthlyBudget();

        BigDecimal usageRate = totalAmount.divide(budgetAmount, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));

        int alertLevel = 0;
        if (budget.getWarningThreshold() != null) {
            BigDecimal warningThreshold = budget.getWarningThreshold();
            if (usageRate.compareTo(warningThreshold) >= 0) {
                alertLevel = 1;
                sendAlert(tenantId, "warning", usageRate, budgetAmount, totalAmount);
            }
        }

        if (budget.getLimitThreshold() != null) {
            BigDecimal limitThreshold = budget.getLimitThreshold();
            if (usageRate.compareTo(limitThreshold) >= 0) {
                alertLevel = 2;
                sendAlert(tenantId, "limit", usageRate, budgetAmount, totalAmount);
                return true;
            }
        }

        budget.setUsedAmount(totalAmount);
        budget.setAlertLevel(alertLevel);
        this.updateById(budget);

        return false;
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

    public void resetMonthlyBudget() {
        LambdaQueryWrapper<TenantBudget> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantBudget::getStatus, "active");
        List<TenantBudget> budgets = this.list(wrapper);

        for (TenantBudget budget : budgets) {
            budget.setUsedAmount(BigDecimal.ZERO);
            budget.setAlertLevel(0);
            budget.setUpdatedAt(LocalDateTime.now());
            this.updateById(budget);
        }

        log.info("月度预算已重置，共重置 {} 个租户", budgets.size());
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