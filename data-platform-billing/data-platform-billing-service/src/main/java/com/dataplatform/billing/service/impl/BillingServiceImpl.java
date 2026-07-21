package com.dataplatform.billing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.billing.entity.BillingDaily;
import com.dataplatform.billing.entity.BillingRule;
import com.dataplatform.billing.entity.BillingRuleTier;
import com.dataplatform.billing.mapper.BillingDailyMapper;
import com.dataplatform.billing.mapper.BillingRuleMapper;
import com.dataplatform.billing.mapper.BillingRuleTierMapper;
import com.dataplatform.billing.mapper.BillingTierUsageMapper;
import com.dataplatform.billing.service.BillingService;
import com.dataplatform.api.Result;
import com.dataplatform.common.billing.BillingCalculatorFactory;
import com.dataplatform.common.entity.unified.BillingRuleDO;
import com.dataplatform.common.entity.unified.BillingTierDO;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
    private BillingRuleTierMapper billingRuleTierMapper;

    @Autowired
    private BillingTierUsageMapper billingTierUsageMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private VendorInternalFeignClient vendorInternalFeignClient;

    @Autowired
    private ApiInterfaceFeignClient apiInterfaceFeignClient;

    @Autowired
    private BillingCalculatorFactory billingCalculatorFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public BigDecimal calculateCost(String vendorCode, String interfaceCode, int callCount, long latency) {
        Long vendorId = resolveVendorId(vendorCode);
        return calculateWithRule(findApplicableRule(vendorId, interfaceCode), callCount, latency);
    }

    @Override
    @Transactional
    public BigDecimal calculateCost(String vendorCode, String interfaceCode, int callCount, long latency,
                                    String requestId, LocalDate billingDate) {
        Long vendorId = resolveVendorId(vendorCode);
        BillingRule rule = findApplicableRule(vendorId, interfaceCode);
        long usageBefore = reserveTierUsage(rule, callCount, requestId, billingDate);
        return calculateWithRule(rule, usageBefore, callCount, latency);
    }

    private BigDecimal calculateWithRule(BillingRule rule, int callCount, long latency) {
        return calculateWithRule(rule, 0L, callCount, latency);
    }

    private BigDecimal calculateWithRule(BillingRule rule, long usageBefore,
                                         int callCount, long latency) {
        if (callCount < 0) {
            throw new IllegalArgumentException("调用次数不能为负数");
        }
        if (rule == null || rule.getUnitPrice() == null) {
            throw new IllegalStateException("没有匹配的启用计费规则");
        }
        Integer latencyMs = latency > 0
                ? (int) Math.min(latency, Integer.MAX_VALUE)
                : null;
        var calculator = billingCalculatorFactory
                .getCalculator(rule.getBillingType() == null ? "STANDARD" : rule.getBillingType());
        BillingRuleDO calculatorRule = toCalculatorRule(rule);
        if (!"TIERED".equals(rule.getBillingType()) || usageBefore == 0L) {
            return calculator.calculate(calculatorRule, callCount, latencyMs);
        }
        long usageAfter = Math.addExact(usageBefore, callCount);
        BigDecimal cumulativeAfter = calculator.calculate(calculatorRule, usageAfter, latencyMs);
        BigDecimal cumulativeBefore = calculator.calculate(calculatorRule, usageBefore, latencyMs);
        return cumulativeAfter.subtract(cumulativeBefore);
    }

    private long reserveTierUsage(BillingRule rule, int callCount,
                                  String requestId, LocalDate billingDate) {
        if (callCount < 0) {
            throw new IllegalArgumentException("调用次数不能为负数");
        }
        if (rule == null || !"TIERED".equals(rule.getBillingType()) || callCount == 0) {
            return 0L;
        }
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            throw new IllegalArgumentException("阶梯计费请求必须提供不超过64位的requestId");
        }
        if (rule.getId() == null) {
            throw new IllegalStateException("阶梯计费规则缺少ID");
        }
        LocalDate period = (billingDate != null ? billingDate : LocalDate.now()).withDayOfMonth(1);
        String lockKey = "billing:tier:" + rule.getId() + ":" + period;
        billingTierUsageMapper.lockUsage(lockKey);

        Long reservedUsage = billingTierUsageMapper.selectUsageBeforeByRequestId(requestId);
        if (reservedUsage != null) {
            return reservedUsage;
        }
        Long currentUsage = billingTierUsageMapper.selectCurrentUsage(rule.getId(), period);
        long usageBefore = currentUsage != null ? currentUsage : 0L;
        billingTierUsageMapper.incrementUsage(rule.getId(), period, callCount);
        billingTierUsageMapper.insertUsageEvent(
                requestId, rule.getId(), period, usageBefore, callCount);
        return usageBefore;
    }

    /**
     * 获取计费规则
     */
    private BillingRule findApplicableRule(Long vendorId, String interfaceCode) {
        if (vendorId == null || interfaceCode == null || interfaceCode.isBlank()) {
            return null;
        }
        return selectApplicableRule(vendorId, interfaceCode);
    }

    private BillingRule selectApplicableRule(Long vendorId, String interfaceCode) {
        LambdaQueryWrapper<BillingRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillingRule::getVendorId, vendorId)
                .eq(BillingRule::getInterfaceCode, interfaceCode)
                .eq(BillingRule::getStatus, "active")
                .last("LIMIT 1");
        return attachTiers(billingRuleMapper.selectOne(wrapper));
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
        if (rule.getTiers() != null) {
            List<BillingTierDO> tiers = rule.getTiers().stream().map(tier -> {
                BillingTierDO calculatorTier = new BillingTierDO();
                calculatorTier.setTierMin(tier.getTierMin());
                calculatorTier.setTierMax(tier.getTierMax());
                calculatorTier.setDiscount(tier.getDiscount());
                return calculatorTier;
            }).toList();
            calculatorRule.setTiers(tiers);
        }
        calculatorRule.setSlaThreshold(rule.getSlaThreshold());
        calculatorRule.setCompensationRate(rule.getCompensationRate());
        calculatorRule.setStatus(rule.getStatus());
        return calculatorRule;
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
        List<BillingRule> rules = billingRuleMapper.selectList(wrapper);
        rules.forEach(this::attachTiers);
        return rules;
    }

    @Override
    public BillingRule getRuleById(Long id) {
        return attachTiers(billingRuleMapper.selectById(id));
    }

    @Override
    @Transactional
    public void saveRule(BillingRule rule) {
        enrichAndValidateAssociation(rule);
        applyRuleDefaults(rule);
        validateAndNormalizeTiers(rule);
        billingRuleMapper.insert(rule);
        replaceTiers(rule);
        evictRuleCache(rule);
    }

    @Override
    @Transactional
    public void updateRule(BillingRule rule) {
        BillingRule existing = getRuleById(rule.getId());
        if (existing == null) {
            throw new IllegalArgumentException("计费规则不存在");
        }
        if (rule.getVendorId() == null) {
            rule.setVendorId(existing.getVendorId());
        }
        if (rule.getInterfaceId() == null) {
            rule.setInterfaceId(existing.getInterfaceId());
        }
        if (rule.getBillingType() == null) {
            rule.setBillingType(existing.getBillingType());
        }
        if (rule.getTiers() == null) {
            rule.setTiers(existing.getTiers());
        }
        enrichAndValidateAssociation(rule);
        applyRuleDefaults(rule);
        validateAndNormalizeTiers(rule);
        billingRuleMapper.updateById(rule);
        replaceTiers(rule);
        evictRuleCache(existing);
        evictRuleCache(rule);
    }

    @Override
    public void deleteRule(Long id) {
        BillingRule existing = getRuleById(id);
        billingRuleMapper.deleteById(id);
        evictRuleCache(existing);
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
    public BillingRule getRuleByVendorAndInterface(String vendorCode, String interfaceCode) {
        if (interfaceCode == null || interfaceCode.isBlank()) {
            return null;
        }

        Long vendorId = resolveVendorId(vendorCode);
        String cacheKey = ruleCacheKey(vendorId, interfaceCode);
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

        BillingRule rule = selectLatestRule(vendorId, interfaceCode);

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

    private BillingRule selectLatestRule(Long vendorId, String interfaceCode) {
        LambdaQueryWrapper<BillingRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillingRule::getVendorId, vendorId)
                .eq(BillingRule::getInterfaceCode, interfaceCode)
                .eq(BillingRule::getStatus, "active")
                .orderByDesc(BillingRule::getCreatedAt)
                .last("LIMIT 1");
        return attachTiers(billingRuleMapper.selectOne(wrapper));
    }

    private void enrichAndValidateAssociation(BillingRule rule) {
        if (rule.getVendorId() == null || rule.getInterfaceId() == null) {
            throw new IllegalArgumentException("vendorId和interfaceId不能为空");
        }

        Result<ApiInterfaceDTO> interfaceResult = apiInterfaceFeignClient.getById(rule.getInterfaceId());
        ApiInterfaceDTO apiInterface = interfaceResult != null ? interfaceResult.getData() : null;
        if (apiInterface == null) {
            throw new IllegalArgumentException("接口不存在或主数据服务不可用: " + rule.getInterfaceId());
        }
        if (!rule.getVendorId().equals(apiInterface.getVendorId())) {
            throw new IllegalArgumentException("所选接口不属于指定厂商");
        }

        Result<VendorInfoDTO> vendorResult = vendorInternalFeignClient.getById(rule.getVendorId());
        VendorInfoDTO vendor = vendorResult != null ? vendorResult.getData() : null;
        if (vendor == null) {
            throw new IllegalArgumentException("厂商不存在或主数据服务不可用: " + rule.getVendorId());
        }

        LambdaQueryWrapper<BillingRule> duplicate = new LambdaQueryWrapper<>();
        duplicate.eq(BillingRule::getVendorId, rule.getVendorId())
                .eq(BillingRule::getInterfaceId, rule.getInterfaceId())
                .ne(rule.getId() != null, BillingRule::getId, rule.getId());
        if (billingRuleMapper.selectCount(duplicate) > 0) {
            throw new IllegalArgumentException("该厂商与接口已存在计费规则");
        }

        rule.setVendorName(vendor.getVendorName());
        rule.setInterfaceCode(apiInterface.getInterfaceCode());
        rule.setInterfaceName(apiInterface.getInterfaceName());
    }

    private void applyRuleDefaults(BillingRule rule) {
        if (rule.getBillingType() == null || rule.getBillingType().isBlank()) {
            rule.setBillingType("STANDARD");
        } else {
            rule.setBillingType(rule.getBillingType().toUpperCase());
        }
        if (rule.getTierMin() == null) {
            rule.setTierMin(0);
        }
        if (rule.getDiscount() == null) {
            rule.setDiscount(BigDecimal.ONE);
        }
        if (rule.getStatus() == null || rule.getStatus().isBlank()) {
            rule.setStatus("active");
        }
    }

    private void validateAndNormalizeTiers(BillingRule rule) {
        if (!"TIERED".equals(rule.getBillingType())) {
            rule.setTiers(new ArrayList<>());
            return;
        }
        if (rule.getTiers() == null || rule.getTiers().isEmpty()) {
            throw new IllegalArgumentException("阶梯计费至少需要配置一个阶梯");
        }

        List<BillingRuleTier> tiers = new ArrayList<>(rule.getTiers());
        tiers.sort(Comparator.comparing(BillingRuleTier::getTierMin,
                Comparator.nullsLast(Long::compareTo)));
        long expectedMin = 0L;
        for (int index = 0; index < tiers.size(); index++) {
            BillingRuleTier tier = tiers.get(index);
            if (tier.getTierMin() == null || tier.getTierMin() != expectedMin) {
                throw new IllegalArgumentException("阶梯区间必须从0开始且连续无间隔");
            }
            if (tier.getDiscount() == null
                    || tier.getDiscount().compareTo(BigDecimal.ZERO) <= 0
                    || tier.getDiscount().compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("阶梯折扣必须大于0且不超过1");
            }
            boolean lastTier = index == tiers.size() - 1;
            if (lastTier) {
                if (tier.getTierMax() != null) {
                    throw new IllegalArgumentException("最后一个阶梯必须设置为无上限");
                }
            } else {
                if (tier.getTierMax() == null || tier.getTierMax() <= tier.getTierMin()) {
                    throw new IllegalArgumentException("非末级阶梯必须配置有效的最大调用量");
                }
                expectedMin = tier.getTierMax();
            }
            tier.setId(null);
            tier.setRuleId(rule.getId());
            tier.setSortOrder(index);
        }
        rule.setTiers(tiers);
        rule.setTierMin(0);
        rule.setTierMax(null);
        rule.setDiscount(tiers.get(0).getDiscount());
    }

    private void replaceTiers(BillingRule rule) {
        LambdaQueryWrapper<BillingRuleTier> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(BillingRuleTier::getRuleId, rule.getId());
        billingRuleTierMapper.delete(deleteWrapper);
        if (rule.getTiers() == null) {
            return;
        }
        for (BillingRuleTier tier : rule.getTiers()) {
            tier.setId(null);
            tier.setRuleId(rule.getId());
            billingRuleTierMapper.insert(tier);
        }
    }

    private BillingRule attachTiers(BillingRule rule) {
        if (rule == null || rule.getId() == null) {
            return rule;
        }
        LambdaQueryWrapper<BillingRuleTier> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BillingRuleTier::getRuleId, rule.getId())
                .orderByAsc(BillingRuleTier::getSortOrder, BillingRuleTier::getTierMin);
        rule.setTiers(billingRuleTierMapper.selectList(wrapper));
        return rule;
    }

    private void evictRuleCache(BillingRule rule) {
        if (rule == null || rule.getVendorId() == null || rule.getInterfaceCode() == null) {
            return;
        }
        try {
            redisTemplate.delete(ruleCacheKey(rule.getVendorId(), rule.getInterfaceCode()));
        } catch (Exception ignored) {
            // Redis unavailable: database remains authoritative.
        }
    }

    private String ruleCacheKey(Long vendorId, String interfaceCode) {
        return RULE_CACHE_PREFIX + vendorId + ":" + interfaceCode;
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
