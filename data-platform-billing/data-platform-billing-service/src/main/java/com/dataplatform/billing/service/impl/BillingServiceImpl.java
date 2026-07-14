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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 计费域计费计算的 Billing Service Impl。
 * <p>业务服务实现，承载本域核心流程编排和事务边界。</p>
 */
@Service
public class BillingServiceImpl extends ServiceImpl<BillingDailyMapper, BillingDaily>
    implements BillingService {

    private static final String RULE_CACHE_PREFIX = "billing:rule:";
    private static final long RULE_CACHE_TTL_SECONDS = 300; // 5 minutes

    @Autowired
    private BillingRuleMapper billingRuleMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 默认单价 (当没有配置计费规则时使用)
    private static final Map<String, BigDecimal> DEFAULT_UNIT_PRICES = Map.of(
        "company_info", new BigDecimal("0.30"),
        "person_phone", new BigDecimal("0.15"),
        "id_card_verify", new BigDecimal("0.20")
    );

    /**
     * 计算费用 - 支持阶梯计费
     */
    @Override
    public BigDecimal calculateCost(String dataType, int callCount) {
        return calculateCost(dataType, callCount, 0);
    }

    /**
     * 计算费用(带响应时间) - 支持SLA补偿
     *
     * SLA补偿公式:
     * - 响应时间超过SLA阈值后，每超100ms，费用减免compensationRate
     * - 例如: SLA=2000ms, 响应时间=2300ms, 补偿率=0.1
     * - 超时300ms，减免系数 = 1 - 0.1 × (300/100) = 1 - 0.3 = 0.7
     * - 实际费用 = 原价 × 0.7
     */
    @Override
    public BigDecimal calculateCost(String dataType, int callCount, long latency) {
        // 1. 获取该数据类型的计费规则
        BillingRule rule = getBillingRule(dataType);
        BigDecimal unitPrice = rule != null && rule.getUnitPrice() != null
            ? rule.getUnitPrice()
            : DEFAULT_UNIT_PRICES.getOrDefault(dataType, BigDecimal.ZERO);

        // 2. 阶梯折扣
        BigDecimal tierDiscount = calculateTierDiscount(callCount, rule);

        // 3. 计算基础费用
        BigDecimal baseCost = unitPrice
            .multiply(BigDecimal.valueOf(callCount))
            .multiply(tierDiscount);

        // 4. SLA补偿 (仅在有响应时间且超过SLA阈值时计算)
        BigDecimal slaDiscount = BigDecimal.ONE;
        if (latency > 0) {
            slaDiscount = calculateSlaDiscount(latency, rule);
        }

        // 5. 计算最终费用
        BigDecimal totalCost = baseCost
            .multiply(slaDiscount)
            .setScale(2, RoundingMode.HALF_UP);

        return totalCost;
    }

    /**
     * 计算SLA补偿折扣
     * 补偿公式: 减免系数 = 1 - compensationRate × (超出时间 / 100ms)
     * 最低折扣为0.1 (最多减免90%)
     */
    private BigDecimal calculateSlaDiscount(long latency, BillingRule rule) {
        // 获取SLA阈值，默认为2000ms
        int slaThreshold = rule != null && rule.getSlaThreshold() != null
            ? rule.getSlaThreshold()
            : 2000;

        // 如果响应时间在SLA内，不补偿
        if (latency <= slaThreshold) {
            return BigDecimal.ONE;
        }

        // 获取补偿率，默认为0.1 (每超100ms减免10%)
        BigDecimal compensationRate = rule != null && rule.getCompensationRate() != null
            ? rule.getCompensationRate()
            : new BigDecimal("0.1");

        // 计算超出时间(毫秒)
        long exceedTime = latency - slaThreshold;

        // 计算减免系数
        BigDecimal discount = BigDecimal.ONE
            .subtract(compensationRate.multiply(BigDecimal.valueOf(exceedTime / 100.0)));

        // 最低折扣0.1
        if (discount.compareTo(new BigDecimal("0.1")) < 0) {
            return new BigDecimal("0.1");
        }

        return discount;
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
        // Daily aggregation is updated incrementally by BillingUsageRecorder.
    }

    @Override
    public Page<BillingDaily> pageQuery(Long tenantId, Long vendorId, LocalDate startDate, LocalDate endDate,
                                         Integer page, Integer pageSize) {
        Page<BillingDaily> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<BillingDaily> wrapper = new LambdaQueryWrapper<>();

        if (tenantId != null) {
            wrapper.eq(BillingDaily::getTenantId, tenantId);
        }
        if (vendorId != null) {
            wrapper.eq(BillingDaily::getVendorId, vendorId);
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

    @Override
    public List<BillingRule> listRules() {
        LambdaQueryWrapper<BillingRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(BillingRule::getCreatedAt);
        return billingRuleMapper.selectList(wrapper);
    }

    @Override
    public BillingRule getRuleById(Long id) {
        return billingRuleMapper.selectById(id);
    }

    @Override
    public void saveRule(BillingRule rule) {
        billingRuleMapper.insert(rule);
    }

    @Override
    public void updateRule(BillingRule rule) {
        billingRuleMapper.updateById(rule);
    }

    @Override
    public void deleteRule(Long id) {
        billingRuleMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBilling", count());
        stats.put("totalRules", billingRuleMapper.selectCount(null));
        return stats;
    }

    @Override
    public byte[] export() {
        StringBuilder csv = new StringBuilder("id,tenant_id,caller_id,vendor_id,data_type,billing_date,call_count,success_count,fail_count,total_cost\n");
        for (BillingDaily billing : list(new LambdaQueryWrapper<BillingDaily>().orderByDesc(BillingDaily::getBillingDate))) {
            csv.append(billing.getId()).append(',')
                    .append(billing.getTenantId()).append(',')
                    .append(billing.getCallerId()).append(',')
                    .append(billing.getVendorId()).append(',')
                    .append(billing.getDataType() == null ? "" : billing.getDataType()).append(',')
                    .append(billing.getBillingDate()).append(',')
                    .append(billing.getCallCount()).append(',')
                    .append(billing.getSuccessCount()).append(',')
                    .append(billing.getFailCount()).append(',')
                    .append(billing.getTotalCost())
                    .append('\n');
        }
        return ("\uFEFF" + csv).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public BillingRule getRuleByVendorAndDataType(String vendorCode, String dataType) {
        if (dataType == null || dataType.isEmpty()) {
            return null;
        }

        String cacheKey = RULE_CACHE_PREFIX + dataType;
        String cachedRule = redisTemplate.opsForValue().get(cacheKey);
        if (cachedRule != null) {
            try {
                return objectMapper.readValue(cachedRule, BillingRule.class);
            } catch (JsonProcessingException e) {
                // 缓存解析失败，继续查询数据库
            }
        }

        LambdaQueryWrapper<BillingRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillingRule::getDataType, dataType);
        wrapper.eq(BillingRule::getStatus, "active");
        wrapper.orderByDesc(BillingRule::getCreatedAt);
        wrapper.last("LIMIT 1");

        BillingRule rule = billingRuleMapper.selectOne(wrapper);

        if (rule != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(rule),
                    RULE_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (JsonProcessingException e) {
                // 缓存写入失败不影响主流程
            }
        }

        return rule;
    }
}
