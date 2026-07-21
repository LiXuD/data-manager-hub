package com.dataplatform.billing.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Access 域执行字段提取所需的最小计量策略快照。
 * Billing 仍是收费判断的唯一权威，Access 只负责按选择器提取事实。
 */
public class BillingMeteringPolicyDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long planId;
    private String planCode;
    private Integer planVersion;
    private String templateCode;
    private String policyHash;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private List<SelectorDTO> selectors = new ArrayList<>();
    private List<BillingAdditionalPlanDTO> additionalPlans = new ArrayList<>();

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public Integer getPlanVersion() { return planVersion; }
    public void setPlanVersion(Integer planVersion) { this.planVersion = planVersion; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getPolicyHash() { return policyHash; }
    public void setPolicyHash(String policyHash) { this.policyHash = policyHash; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
    public List<SelectorDTO> getSelectors() { return selectors; }
    public void setSelectors(List<SelectorDTO> selectors) {
        this.selectors = selectors != null ? selectors : new ArrayList<>();
    }
    public List<BillingAdditionalPlanDTO> getAdditionalPlans() { return additionalPlans; }
    public void setAdditionalPlans(List<BillingAdditionalPlanDTO> additionalPlans) {
        this.additionalPlans = additionalPlans != null ? additionalPlans : new ArrayList<>();
    }

    public static class SelectorDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String alias;
        private String source;
        private Long fieldId;
        private String path;
        private String extraction;

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
    }
}
