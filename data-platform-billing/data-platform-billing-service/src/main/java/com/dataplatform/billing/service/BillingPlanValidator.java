package com.dataplatform.billing.service;

import com.dataplatform.billing.model.BillingPlanModel;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 发布前完整校验，防止无法执行或存在财务歧义的方案进入生产。 */
@Component
public class BillingPlanValidator {

    private static final Set<String> TEMPLATES = Set.of(
            "PER_CALL", "TIERED", "PACKAGE_COUNT", "FLAT_PERIOD", "PER_ITEM", "DURATION");
    private static final Set<String> OPERATORS = Set.of(
            "EXISTS", "NOT_EXISTS", "EMPTY", "NOT_EMPTY", "TRUE", "FALSE",
            "EQ", "NE", "IN", "NOT_IN", "GT", "GTE", "LT", "LTE");
    private static final Set<String> MISSING_POLICIES = Set.of(
            "PENDING_REVIEW", "NOT_BILLABLE", "BILLABLE", "ERROR");
    private static final Set<String> CACHE_POLICIES = Set.of("FREE", "SAME_PRICE", "CUSTOM");

    public List<String> validate(BillingPlanModel plan, InterfaceContractDTO contract) {
        List<String> errors = new ArrayList<>();
        if (plan == null) return List.of("计费方案不能为空");
        required(plan.getPlanName(), "方案名称", errors);
        required(plan.getTemplateCode(), "计费模板", errors);
        if (plan.getVendorId() == null) errors.add("厂商不能为空");
        if (plan.getInterfaceId() == null) errors.add("接口不能为空");
        if (!TEMPLATES.contains(upper(plan.getTemplateCode()))) errors.add("不支持的计费模板");
        if (!Set.of("VENDOR_PAYABLE", "INTERNAL_CHARGEBACK").contains(upper(plan.getAccountingPurpose()))) {
            errors.add("计费方向仅支持VENDOR_PAYABLE或INTERNAL_CHARGEBACK");
        }
        if (plan.getCurrency() == null || !plan.getCurrency().matches("[A-Z]{3}")) {
            errors.add("币种必须是三位大写代码");
        }
        if (!Set.of("DAY", "MONTH", "YEAR").contains(upper(plan.getSettlementCycle()))) {
            errors.add("结算周期仅支持DAY、MONTH、YEAR");
        }
        try {
            ZoneId.of(plan.getTimezone());
        } catch (Exception exception) {
            errors.add("时区无效");
        }
        if (plan.getEffectiveFrom() == null) errors.add("生效时间不能为空");
        if (plan.getEffectiveFrom() != null && plan.getEffectiveTo() != null
                && !plan.getEffectiveTo().isAfter(plan.getEffectiveFrom())) {
            errors.add("失效时间必须晚于生效时间");
        }
        validatePricing(plan, errors);
        validateMetering(plan, contract, errors);
        validateAdjustments(plan, errors);
        return errors;
    }

    private void validatePricing(BillingPlanModel plan, List<String> errors) {
        BillingPlanModel.PricingConfig pricing = plan.getPricing();
        if (pricing == null) {
            errors.add("价格配置不能为空");
            return;
        }
        String template = upper(plan.getTemplateCode());
        if (Set.of("PER_CALL", "PER_ITEM", "DURATION").contains(template)) {
            nonNegative(pricing.getUnitPrice(), "单价", errors);
        }
        if ("TIERED".equals(template)) {
            nonNegative(pricing.getUnitPrice(), "阶梯基础单价", errors);
            validateTiers(plan.getTiers(), errors);
        }
        if ("PACKAGE_COUNT".equals(template)) {
            nonNegative(pricing.getPackageFee(), "套餐费", errors);
            positive(pricing.getIncludedUnits(), "套餐包含量", errors);
            nonNegative(pricing.getOverageUnitPrice(), "超额单价", errors);
            if (Boolean.TRUE.equals(pricing.getCarryOver())) {
                errors.add("当前版本未启用套餐结转，carryOver必须为false");
            }
        }
        if ("FLAT_PERIOD".equals(template)) {
            nonNegative(pricing.getPackageFee(), "周期固定费用", errors);
        }
        if (plan.getMetering() != null
                && "CUSTOM".equals(upper(plan.getMetering().getCacheBillingPolicy()))) {
            nonNegative(pricing.getCacheUnitPrice(), "缓存自定义单价", errors);
        }
    }

    private void validateTiers(List<BillingPlanModel.TierConfig> source, List<String> errors) {
        if (source == null || source.isEmpty()) {
            errors.add("阶梯计费至少需要一个阶梯");
            return;
        }
        List<BillingPlanModel.TierConfig> tiers = source.stream()
                .sorted(Comparator.comparing(BillingPlanModel.TierConfig::getTierMin,
                        Comparator.nullsLast(BigDecimal::compareTo))).toList();
        BigDecimal expected = BigDecimal.ZERO;
        for (int index = 0; index < tiers.size(); index++) {
            BillingPlanModel.TierConfig tier = tiers.get(index);
            if (tier.getTierMin() == null || tier.getTierMin().compareTo(expected) != 0) {
                errors.add("阶梯必须从0开始且连续无间隔");
                return;
            }
            boolean last = index == tiers.size() - 1;
            if (last && tier.getTierMax() != null) errors.add("最后一个阶梯必须无上限");
            if (!last && (tier.getTierMax() == null
                    || tier.getTierMax().compareTo(tier.getTierMin()) <= 0)) {
                errors.add("非末级阶梯必须设置有效上限");
                return;
            }
            if (tier.getDiscount() == null || tier.getDiscount().signum() <= 0
                    || tier.getDiscount().compareTo(BigDecimal.ONE) > 0) {
                errors.add("阶梯折扣必须大于0且不超过1");
            }
            if (tier.getUnitPrice() != null && tier.getUnitPrice().signum() < 0) {
                errors.add("阶梯单价不能为负数");
            }
            if (!last) expected = tier.getTierMax();
        }
    }

    private void validateMetering(BillingPlanModel plan, InterfaceContractDTO contract,
                                  List<String> errors) {
        BillingPlanModel.MeteringConfig metering = plan.getMetering();
        if (metering == null) {
            errors.add("计量配置不能为空");
            return;
        }
        if (!Set.of("AND", "OR").contains(upper(metering.getLogic()))) errors.add("条件逻辑无效");
        if (!MISSING_POLICIES.contains(upper(metering.getMissingFieldPolicy()))) {
            errors.add("字段缺失策略无效");
        }
        if (!CACHE_POLICIES.contains(upper(metering.getCacheBillingPolicy()))) {
            errors.add("缓存计费策略无效");
        }
        if (!Set.of("VENDOR_INTERFACE", "TENANT", "CALLER")
                .contains(upper(metering.getAggregationScope()))) {
            errors.add("累计范围无效");
        }
        Map<Long, FieldInfo> byId = new HashMap<>();
        Map<String, FieldInfo> byPath = new HashMap<>();
        if (contract != null) flatten(contract.getResponseFields(), "$.data", byId, byPath);
        Set<String> aliases = new HashSet<>();
        List<BillingPlanModel.ConditionConfig> conditions = metering.getConditions() != null
                ? metering.getConditions() : List.of();
        for (int index = 0; index < conditions.size(); index++) {
            BillingPlanModel.ConditionConfig condition = conditions.get(index);
            String alias = condition.getAlias() == null || condition.getAlias().isBlank()
                    ? "condition-" + index : condition.getAlias();
            if (!aliases.add(alias)) errors.add("计量字段别名重复: " + alias);
            if (!OPERATORS.contains(upper(condition.getOperator()))) errors.add(alias + "操作符无效");
            validateFieldReference(alias, condition.getSource(), condition.getFieldId(),
                    condition.getPath(), condition.getExtraction(), byId, byPath, errors);
        }
        BillingPlanModel.QuantityConfig quantity = metering.getQuantity();
        if (quantity == null) {
            errors.add("计费数量配置不能为空");
            return;
        }
        if (!Set.of("FIXED", "FACT", "ARRAY_SIZE", "DURATION").contains(upper(quantity.getType()))) {
            errors.add("计费数量类型无效");
        }
        if (quantity.getUnit() == null || quantity.getUnit().isBlank()) {
            errors.add("计费数量单位不能为空");
        }
        String template = upper(plan.getTemplateCode());
        if ("DURATION".equals(template) && !"DURATION".equals(upper(quantity.getType()))) {
            errors.add("按时长模板的计费数量类型必须是DURATION");
        }
        if ("PER_ITEM".equals(template)
                && !Set.of("FACT", "ARRAY_SIZE").contains(upper(quantity.getType()))) {
            errors.add("按返回数据量模板必须选择响应数值或数组长度");
        }
        if ("FIXED".equals(upper(quantity.getType()))) {
            positive(quantity.getFixedValue(), "固定计费数量", errors);
        } else if (!"DURATION".equals(upper(quantity.getType()))) {
            validateFieldReference("quantity", quantity.getSource(), quantity.getFieldId(),
                    quantity.getPath(), quantity.getExtraction(), byId, byPath, errors);
        }
    }

    private void validateFieldReference(String label, String source, Long fieldId, String path,
                                        String extraction, Map<Long, FieldInfo> byId,
                                        Map<String, FieldInfo> byPath, List<String> errors) {
        String normalizedSource = upper(source);
        if ("METADATA".equals(normalizedSource)) {
            if (!Set.of("success", "$.success", "cached", "$.cached", "responseContractValid",
                    "$.responseContractValid", "latencyMs", "$.latencyMs", "httpStatus", "$.httpStatus")
                    .contains(path)) errors.add(label + "引用了不支持的元数据字段");
            return;
        }
        if (!Set.of("NORMALIZED_RESPONSE", "REQUEST").contains(normalizedSource)) {
            errors.add(label + "字段来源无效");
            return;
        }
        if (path == null || path.isBlank()) {
            errors.add(label + "字段路径不能为空");
            return;
        }
        if ("REQUEST".equals(normalizedSource)) return;
        if ("$.success".equals(path)) return;
        FieldInfo info = byPath.get(path);
        if (info == null) {
            errors.add(label + "字段不在当前接口响应契约中: " + path);
            return;
        }
        if (fieldId != null && (byId.get(fieldId) == null || !path.equals(byId.get(fieldId).path()))) {
            errors.add(label + "字段ID与路径不匹配");
        }
        if ("ARRAY_SIZE".equals(upper(extraction)) && !"array".equalsIgnoreCase(info.type())) {
            errors.add(label + "只有数组字段可以提取ARRAY_SIZE");
        }
    }

    private void validateAdjustments(BillingPlanModel plan, List<String> errors) {
        BillingPlanModel.AdjustmentConfig adjustment = plan.getAdjustment();
        if (adjustment == null || !Boolean.TRUE.equals(adjustment.getSlaEnabled())) return;
        if (adjustment.getSlaThresholdMs() == null || adjustment.getSlaThresholdMs() < 0) {
            errors.add("SLA阈值必须大于等于0");
        }
        if (adjustment.getCompensationRatePer100Ms() == null
                || adjustment.getCompensationRatePer100Ms().signum() < 0
                || adjustment.getCompensationRatePer100Ms().compareTo(BigDecimal.ONE) > 0) {
            errors.add("SLA每100ms补偿率必须在0到1之间");
        }
    }

    private void flatten(List<InterfaceParamDTO> fields, String parent,
                         Map<Long, FieldInfo> byId, Map<String, FieldInfo> byPath) {
        if (fields == null) return;
        for (InterfaceParamDTO field : fields) {
            String path = parent + "." + field.getParamName();
            FieldInfo info = new FieldInfo(path, field.getParamType());
            if (field.getId() != null) byId.put(field.getId(), info);
            byPath.put(path, info);
            flatten(field.getChildren(), path, byId, byPath);
        }
    }

    private void required(String value, String label, List<String> errors) {
        if (value == null || value.isBlank()) errors.add(label + "不能为空");
    }

    private void nonNegative(BigDecimal value, String label, List<String> errors) {
        if (value == null || value.signum() < 0) errors.add(label + "不能为空或为负数");
    }

    private void positive(BigDecimal value, String label, List<String> errors) {
        if (value == null || value.signum() <= 0) errors.add(label + "必须大于0");
    }

    private String upper(String value) {
        return value == null ? "" : value.toUpperCase();
    }

    private record FieldInfo(String path, String type) {
    }
}
