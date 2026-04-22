package com.dataplatform.billing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.billing.entity.BillingDaily;
import com.dataplatform.billing.entity.BillingRule;
import com.dataplatform.billing.mapper.BillingDailyMapper;
import com.dataplatform.billing.mapper.BillingRuleMapper;
import com.dataplatform.billing.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BillingServiceImpl extends ServiceImpl<BillingDailyMapper, BillingDaily>
    implements BillingService {

    @Autowired
    private BillingRuleMapper billingRuleMapper;

    // 默认单价 (当没有配置计费规则时使用)
    private static final Map<String, BigDecimal> DEFAULT_UNIT_PRICES = Map.of(
        "company_info", new BigDecimal("0.30"),
        "person_phone", new BigDecimal("0.15"),
        "id_card_verify", new BigDecimal("0.20")
    );

    /**
     * 计算费用 - 支持阶梯计费
     *
     * 计费公式:
     * - 标准计费: 费用 = 调用次数 × 单价
     * - 阶梯计费: 根据调用量应用不同折扣
     *   - 0-10万次: 单价×1.0
     *   - 10-50万次: 单价×0.9
     *   - 50万次以上: 单价×0.8
     */
    @Override
    public BigDecimal calculateCost(String dataType, int callCount) {
        // 1. 获取该数据类型的计费规则
        BillingRule rule = getBillingRule(dataType);
        BigDecimal unitPrice = rule != null && rule.getUnitPrice() != null
            ? rule.getUnitPrice()
            : DEFAULT_UNIT_PRICES.getOrDefault(dataType, BigDecimal.ZERO);

        // 2. 如果有阶梯折扣，应用折扣
        BigDecimal discount = calculateTierDiscount(callCount, rule);

        // 3. 计算总费用
        BigDecimal totalCost = unitPrice
            .multiply(BigDecimal.valueOf(callCount))
            .multiply(discount)
            .setScale(2, RoundingMode.HALF_UP);

        return totalCost;
    }

    /**
     * 获取计费规则
     */
    private BillingRule getBillingRule(String dataType) {
        LambdaQueryWrapper<BillingRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillingRule::getDataType, dataType);
        wrapper.eq(BillingRule::getStatus, "active");
        wrapper.orderByDesc(BillingRule::getCreatedAt);
        wrapper.last("LIMIT 1");

        List<BillingRule> rules = billingRuleMapper.selectList(wrapper);
        return rules.isEmpty() ? null : rules.get(0);
    }

    /**
     * 计算阶梯折扣
     */
    private BigDecimal calculateTierDiscount(int callCount, BillingRule rule) {
        // 如果有自定义规则，使用规则中的折扣
        if (rule != null && rule.getDiscount() != null) {
            return rule.getDiscount();
        }

        // 默认阶梯折扣
        if (callCount > 500000) {
            return new BigDecimal("0.8");  // 50万次以上 8折
        } else if (callCount > 100000) {
            return new BigDecimal("0.9");  // 10-50万次 9折
        } else {
            return BigDecimal.ONE;          // 10万次以下 不打折
        }
    }

    /**
     * 计算单次调用的费用
     */
    public BigDecimal calculateSingleCost(String dataType) {
        return calculateCost(dataType, 1);
    }

    @Override
    public void recordDailyBilling(LocalDate billingDate) {
        // 从call_record聚合数据，写入billing_daily
        // TODO: 实际应从Kafka消费或查询数据库
    }

    @Override
    public Page<BillingDaily> pageQuery(Long tenantId, LocalDate startDate, LocalDate endDate,
                                         Integer page, Integer pageSize) {
        Page<BillingDaily> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<BillingDaily> wrapper = new LambdaQueryWrapper<>();

        if (tenantId != null) {
            wrapper.eq(BillingDaily::getTenantId, tenantId);
        }
        if (startDate != null) {
            wrapper.ge(BillingDaily::getBillingDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(BillingDaily::getBillingDate, endDate);
        }

        wrapper.orderByDesc(BillingDaily::getBillingDate);
        return page(pageParam, wrapper);
    }

    @Override
    public Map<String, Object> getBillingStats(Long tenantId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<BillingDaily> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) {
            wrapper.eq(BillingDaily::getTenantId, tenantId);
        }
        if (startDate != null) {
            wrapper.ge(BillingDaily::getBillingDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(BillingDaily::getBillingDate, endDate);
        }

        List<BillingDaily> list = list(wrapper);

        long totalCallCount = list.stream().mapToLong(BillingDaily::getCallCount).sum();
        BigDecimal totalCost = list.stream()
            .map(BillingDaily::getTotalCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCallCount", totalCallCount);
        stats.put("totalCost", totalCost);
        stats.put("days", list.size());

        return stats;
    }
}
