package com.dataplatform.billing.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** 提交给 Billing 的最小计量事实，不携带厂商完整响应。 */
public class BillingChargeReqDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Long planId;
    private Integer planVersion;
    private String policyHash;
    private String vendorCode;
    private String interfaceCode;
    private String accountingPurpose;
    private String dataType;
    private Long tenantId;
    private Long callerId;
    private Long vendorId;
    private LocalDateTime callTime;
    private Boolean success;
    private Boolean cached;
    private Boolean responseContractValid;
    private Long latencyMs;
    private Integer httpStatus;
    private Map<String, Object> meteringFacts = new LinkedHashMap<>();
    private List<BillingAdditionalPlanDTO> additionalPlans = new ArrayList<>();

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Integer getPlanVersion() { return planVersion; }
    public void setPlanVersion(Integer planVersion) { this.planVersion = planVersion; }
    public String getPolicyHash() { return policyHash; }
    public void setPolicyHash(String policyHash) { this.policyHash = policyHash; }
    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    public String getInterfaceCode() { return interfaceCode; }
    public void setInterfaceCode(String interfaceCode) { this.interfaceCode = interfaceCode; }
    public String getAccountingPurpose() { return accountingPurpose; }
    public void setAccountingPurpose(String accountingPurpose) { this.accountingPurpose = accountingPurpose; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getCallerId() { return callerId; }
    public void setCallerId(Long callerId) { this.callerId = callerId; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public LocalDateTime getCallTime() { return callTime; }
    public void setCallTime(LocalDateTime callTime) { this.callTime = callTime; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public Boolean getCached() { return cached; }
    public void setCached(Boolean cached) { this.cached = cached; }
    public Boolean getResponseContractValid() { return responseContractValid; }
    public void setResponseContractValid(Boolean responseContractValid) { this.responseContractValid = responseContractValid; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public Map<String, Object> getMeteringFacts() { return meteringFacts; }
    public void setMeteringFacts(Map<String, Object> meteringFacts) {
        this.meteringFacts = meteringFacts != null ? meteringFacts : new LinkedHashMap<>();
    }
    public List<BillingAdditionalPlanDTO> getAdditionalPlans() { return additionalPlans; }
    public void setAdditionalPlans(List<BillingAdditionalPlanDTO> additionalPlans) {
        this.additionalPlans = additionalPlans != null ? additionalPlans : new ArrayList<>();
    }
}
