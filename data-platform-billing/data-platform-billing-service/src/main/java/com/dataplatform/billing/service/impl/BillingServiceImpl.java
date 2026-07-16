package com.dataplatform.billing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.billing.entity.BillingDaily;
import com.dataplatform.billing.entity.BillingRule;
import com.dataplatform.billing.mapper.BillingDailyMapper;
import com.dataplatform.billing.mapper.BillingRuleMapper;
import com.dataplatform.billing.service.BillingService;
import com.dataplatform.api.Result;
import com.dataplatform.common.billing.BillingCalculatorFactory;
import com.dataplatform.common.entity.unified.BillingRuleDO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Autowired
    private VendorInternalFeignClient vendorInternalFeignClient;

    @Autowired
    private BillingCalculatorFactory billingCalculatorFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        return calculateWithRule(findApplicableRule(null, dataType, callCount), callCount, latency);
    }

    @Override
    public BigDecimal calculateCost(String vendorCode, String dataType, int callCount, long latency) {
        Long vendorId = resolveVendorId(vendorCode);
        return calculateWithRule(findApplicableRule(vendorId, dataType, callCount), callCount, latency);
    }

    private BigDecimal calculateWithRule(BillingRule rule, int callCount, long latency) {
        if (callCount < 0) {
            throw new IllegalArgumentException("调用次数不能为负数");
        }
        if (rule == null || rule.getUnitPrice() == null) {
            throw new IllegalStateException("没有匹配的启用计费规则");
        }
        Integer latencyMs = latency > 0
                ? (int) Math.min(latency, Integer.MAX_VALUE)
                : null;
        return billingCalculatorFactory
                .getCalculator(rule.getBillingType() == null ? "STANDARD" : rule.getBillingType())
                .calculate(toCalculatorRule(rule), callCount, latencyMs);
    }

    /**
     * 获取计费规则
     */
    private BillingRule findApplicableRule(Long vendorId, String dataType, int callCount) {
        BillingRule vendorRule = vendorId == null ? null
                : selectApplicableRule(vendorId, dataType, callCount);
        return vendorRule != null ? vendorRule : selectApplicableRule(null, dataType, callCount);
    }

    private BillingRule selectApplicableRule(Long vendorId, String dataType, int callCount) {
        LambdaQueryWrapper<BillingRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillingRule::getDataType, dataType)
                .eq(BillingRule::getStatus, "active")
                .le(BillingRule::getTierMin, callCount)
                .and(query -> query.isNull(BillingRule::getTierMax)
                        .or().ge(BillingRule::getTierMax, callCount));
        wrapper.eq(vendorId != null, BillingRule::getVendorId, vendorId)
                .isNull(vendorId == null, BillingRule::getVendorId);
        wrapper.orderByDesc(BillingRule::getTierMin)
                .orderByDesc(BillingRule::getCreatedAt)
                .last("LIMIT 1");
        return billingRuleMapper.selectOne(wrapper);
    }

    private BillingRuleDO toCalculatorRule(BillingRule rule) {
        BillingRuleDO calculatorRule = new BillingRuleDO();
        calculatorRule.setId(rule.getId());
        calculatorRule.setVendorId(rule.getVendorId());
        calculatorRule.setVendorName(rule.getVendorName());
        calculatorRule.setDataType(rule.getDataType());
        calculatorRule.setBillingType(rule.getBillingType());
        calculatorRule.setUnitPrice(rule.getUnitPrice());
        calculatorRule.setTierMin(rule.getTierMin());
        calculatorRule.setTierMax(rule.getTierMax());
        calculatorRule.setDiscount(rule.getDiscount());
        calculatorRule.setSlaThreshold(rule.getSlaThreshold());
        calculatorRule.setCompensationRate(rule.getCompensationRate());
        calculatorRule.setStatus(rule.getStatus());
        return calculatorRule;
    }

    /**
     * 计算单次调用的费用
     */
    public BigDecimal calculateSingleCost(String dataType) {
        return calculateCost(dataType, 1);
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

        Long vendorId = resolveVendorId(vendorCode);
        String cacheKey = RULE_CACHE_PREFIX + (vendorId == null ? "global" : vendorId) + ":" + dataType;
        String cachedRule = null;
        try {
            cachedRule = redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception ignored) {
            // Redis unavailable: continue with the authoritative database.
        }
        if (cachedRule != null) {
            try {
                return objectMapper.readValue(cachedRule, BillingRule.class);
            } catch (JsonProcessingException e) {
                // 缓存解析失败，继续查询数据库
            }
        }

        BillingRule rule = selectLatestRule(vendorId, dataType);
        if (rule == null && vendorId != null) {
            rule = selectLatestRule(null, dataType);
        }

        if (rule != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(rule),
                        RULE_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                // 缓存写入失败不影响主流程
            }
        }

        return rule;
    }

    private BillingRule selectLatestRule(Long vendorId, String dataType) {
        LambdaQueryWrapper<BillingRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillingRule::getDataType, dataType)
                .eq(BillingRule::getStatus, "active")
                .eq(vendorId != null, BillingRule::getVendorId, vendorId)
                .isNull(vendorId == null, BillingRule::getVendorId)
                .orderByDesc(BillingRule::getCreatedAt)
                .last("LIMIT 1");
        return billingRuleMapper.selectOne(wrapper);
    }

    private Long resolveVendorId(String vendorCode) {
        if (vendorCode == null || vendorCode.isBlank()) {
            return null;
        }
        Result<VendorInfoDTO> response = vendorInternalFeignClient.getByVendorCode(vendorCode);
        if (response == null || !Integer.valueOf(200).equals(response.getCode())
                || response.getData() == null || response.getData().getId() == null) {
            throw new IllegalArgumentException("厂商不存在或主数据服务不可用: " + vendorCode);
        }
        return response.getData().getId();
    }
}
