package com.dataplatform.billing.api.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 同一次数据调用需要并行入账的附加会计方向方案。 */
public class BillingAdditionalPlanDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long planId;
    private String planCode;
    private Integer planVersion;
    private String templateCode;
    private String accountingPurpose;
    private String policyHash;
    private List<BillingMeteringPolicyDTO.SelectorDTO> selectors = new ArrayList<>();
    private Map<String, Object> meteringFacts = new LinkedHashMap<>();

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public Integer getPlanVersion() { return planVersion; }
    public void setPlanVersion(Integer planVersion) { this.planVersion = planVersion; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getAccountingPurpose() { return accountingPurpose; }
    public void setAccountingPurpose(String accountingPurpose) { this.accountingPurpose = accountingPurpose; }
    public String getPolicyHash() { return policyHash; }
    public void setPolicyHash(String policyHash) { this.policyHash = policyHash; }
    public List<BillingMeteringPolicyDTO.SelectorDTO> getSelectors() { return selectors; }
    public void setSelectors(List<BillingMeteringPolicyDTO.SelectorDTO> selectors) {
        this.selectors = selectors != null ? selectors : new ArrayList<>();
    }
    public Map<String, Object> getMeteringFacts() { return meteringFacts; }
    public void setMeteringFacts(Map<String, Object> meteringFacts) {
        this.meteringFacts = meteringFacts != null ? meteringFacts : new LinkedHashMap<>();
    }
}
