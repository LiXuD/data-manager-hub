package com.dataplatform.billing.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 计费方案管理端的强类型命令和视图模型。 */

public class BillingPlanModel {
    private Long id;
    private String planCode;
    private Integer version;
    private String planName;
    private Long vendorId;
    private String vendorCode;
    private String vendorName;
    private Long interfaceId;
    private String interfaceCode;
    private String interfaceName;
    private String templateCode;
    private String accountingPurpose = "VENDOR_PAYABLE";
    private String currency = "CNY";
    private String timezone = "Asia/Shanghai";
    private String settlementCycle = "MONTH";
    private String status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String contractFingerprint;
    private PricingConfig pricing = new PricingConfig();
    private MeteringConfig metering = new MeteringConfig();
    private AdjustmentConfig adjustment = new AdjustmentConfig();
    private List<TierConfig> tiers = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public Long getInterfaceId() { return interfaceId; }
    public void setInterfaceId(Long interfaceId) { this.interfaceId = interfaceId; }
    public String getInterfaceCode() { return interfaceCode; }
    public void setInterfaceCode(String interfaceCode) { this.interfaceCode = interfaceCode; }
    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getAccountingPurpose() { return accountingPurpose; }
    public void setAccountingPurpose(String accountingPurpose) { this.accountingPurpose = accountingPurpose; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getSettlementCycle() { return settlementCycle; }
    public void setSettlementCycle(String settlementCycle) { this.settlementCycle = settlementCycle; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getContractFingerprint() { return contractFingerprint; }
    public void setContractFingerprint(String contractFingerprint) { this.contractFingerprint = contractFingerprint; }
    public PricingConfig getPricing() { return pricing; }
    public void setPricing(PricingConfig pricing) { this.pricing = pricing; }
    public MeteringConfig getMetering() { return metering; }
    public void setMetering(MeteringConfig metering) { this.metering = metering; }
    public AdjustmentConfig getAdjustment() { return adjustment; }
    public void setAdjustment(AdjustmentConfig adjustment) { this.adjustment = adjustment; }
    public List<TierConfig> getTiers() { return tiers; }
    public void setTiers(List<TierConfig> tiers) { this.tiers = tiers; }
    public static class PricingConfig {
        private BigDecimal unitPrice = BigDecimal.ZERO;
        private BigDecimal packageFee = BigDecimal.ZERO;
        private BigDecimal includedUnits = BigDecimal.ZERO;
        private BigDecimal overageUnitPrice = BigDecimal.ZERO;
        private BigDecimal cacheUnitPrice;
        private String tierMode = "GRADUATED";
        private String durationUnit = "SECOND";
        private String durationRounding = "CEILING";
        private Boolean carryOver = false;

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public BigDecimal getPackageFee() { return packageFee; }
        public void setPackageFee(BigDecimal packageFee) { this.packageFee = packageFee; }
        public BigDecimal getIncludedUnits() { return includedUnits; }
        public void setIncludedUnits(BigDecimal includedUnits) { this.includedUnits = includedUnits; }
        public BigDecimal getOverageUnitPrice() { return overageUnitPrice; }
        public void setOverageUnitPrice(BigDecimal overageUnitPrice) { this.overageUnitPrice = overageUnitPrice; }
        public BigDecimal getCacheUnitPrice() { return cacheUnitPrice; }
        public void setCacheUnitPrice(BigDecimal cacheUnitPrice) { this.cacheUnitPrice = cacheUnitPrice; }
        public String getTierMode() { return tierMode; }
        public void setTierMode(String tierMode) { this.tierMode = tierMode; }
        public String getDurationUnit() { return durationUnit; }
        public void setDurationUnit(String durationUnit) { this.durationUnit = durationUnit; }
        public String getDurationRounding() { return durationRounding; }
        public void setDurationRounding(String durationRounding) { this.durationRounding = durationRounding; }
        public Boolean getCarryOver() { return carryOver; }
        public void setCarryOver(Boolean carryOver) { this.carryOver = carryOver; }
    }

    public static class MeteringConfig {
        private String logic = "AND";
        private List<ConditionConfig> conditions = new ArrayList<>();
        private QuantityConfig quantity = new QuantityConfig();
        private String missingFieldPolicy = "PENDING_REVIEW";
        private String cacheBillingPolicy = "FREE";
        private String aggregationScope = "VENDOR_INTERFACE";

        public String getLogic() { return logic; }
        public void setLogic(String logic) { this.logic = logic; }
        public List<ConditionConfig> getConditions() { return conditions; }
        public void setConditions(List<ConditionConfig> conditions) { this.conditions = conditions; }
        public QuantityConfig getQuantity() { return quantity; }
        public void setQuantity(QuantityConfig quantity) { this.quantity = quantity; }
        public String getMissingFieldPolicy() { return missingFieldPolicy; }
        public void setMissingFieldPolicy(String missingFieldPolicy) { this.missingFieldPolicy = missingFieldPolicy; }
        public String getCacheBillingPolicy() { return cacheBillingPolicy; }
        public void setCacheBillingPolicy(String cacheBillingPolicy) { this.cacheBillingPolicy = cacheBillingPolicy; }
        public String getAggregationScope() { return aggregationScope; }
        public void setAggregationScope(String aggregationScope) { this.aggregationScope = aggregationScope; }
    }

    public static class ConditionConfig {
        private String alias;
        private String source = "NORMALIZED_RESPONSE";
        private Long fieldId;
        private String path;
        private String extraction = "VALUE";
        private String operator = "EQ";
        private Object expectedValue;

        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public Long getFieldId() { return fieldId; }
        public void setFieldId(Long fieldId) { this.fieldId = fieldId; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getExtraction() { return extraction; }
        public void setExtraction(String extraction) { this.extraction = extraction; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public Object getExpectedValue() { return expectedValue; }
        public void setExpectedValue(Object expectedValue) { this.expectedValue = expectedValue; }
    }

    public static class QuantityConfig {
        private String type = "FIXED";
        private String alias = "quantity";
        private String source = "NORMALIZED_RESPONSE";
        private Long fieldId;
        private String path;
        private String extraction = "VALUE";
        private BigDecimal fixedValue = BigDecimal.ONE;
        private String unit = "CALL";

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public Long getFieldId() { return fieldId; }
        public void setFieldId(Long fieldId) { this.fieldId = fieldId; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getExtraction() { return extraction; }
        public void setExtraction(String extraction) { this.extraction = extraction; }
        public BigDecimal getFixedValue() { return fixedValue; }
        public void setFixedValue(BigDecimal fixedValue) { this.fixedValue = fixedValue; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
    }

    public static class AdjustmentConfig {
        private Boolean noChargeOnFailure = true;
        private Boolean requireValidContract = false;
        private Boolean slaEnabled = false;
        private Long slaThresholdMs;
        private BigDecimal compensationRatePer100Ms;

        public Boolean getNoChargeOnFailure() { return noChargeOnFailure; }
        public void setNoChargeOnFailure(Boolean noChargeOnFailure) { this.noChargeOnFailure = noChargeOnFailure; }
        public Boolean getRequireValidContract() { return requireValidContract; }
        public void setRequireValidContract(Boolean requireValidContract) { this.requireValidContract = requireValidContract; }
        public Boolean getSlaEnabled() { return slaEnabled; }
        public void setSlaEnabled(Boolean slaEnabled) { this.slaEnabled = slaEnabled; }
        public Long getSlaThresholdMs() { return slaThresholdMs; }
        public void setSlaThresholdMs(Long slaThresholdMs) { this.slaThresholdMs = slaThresholdMs; }
        public BigDecimal getCompensationRatePer100Ms() { return compensationRatePer100Ms; }
        public void setCompensationRatePer100Ms(BigDecimal compensationRatePer100Ms) { this.compensationRatePer100Ms = compensationRatePer100Ms; }
    }

    public static class TierConfig {
        private Long id;
        private BigDecimal tierMin;
        private BigDecimal tierMax;
        private BigDecimal unitPrice;
        private BigDecimal discount = BigDecimal.ONE;
        private Integer sortOrder;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public BigDecimal getTierMin() { return tierMin; }
        public void setTierMin(BigDecimal tierMin) { this.tierMin = tierMin; }
        public BigDecimal getTierMax() { return tierMax; }
        public void setTierMax(BigDecimal tierMax) { this.tierMax = tierMax; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public BigDecimal getDiscount() { return discount; }
        public void setDiscount(BigDecimal discount) { this.discount = discount; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }
}
