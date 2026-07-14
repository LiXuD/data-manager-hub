package com.dataplatform.billing.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 计费域计费计算的 Billing Calculate Req DTO。
 * <p>跨服务契约数据对象，用于 api 模块暴露远程接口时传递稳定字段。</p>
 */
public class BillingCalculateReqDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String vendorCode;
    private String dataType;
    private Integer callCount;
    private Long latency;
    private String requestId;
    private Long tenantId;
    private Long callerId;
    private Long vendorId;
    private Boolean success;
    private Boolean billable;
    private LocalDateTime callTime;

    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public Integer getCallCount() { return callCount; }
    public void setCallCount(Integer callCount) { this.callCount = callCount; }
    public Long getLatency() { return latency; }
    public void setLatency(Long latency) { this.latency = latency; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getCallerId() { return callerId; }
    public void setCallerId(Long callerId) { this.callerId = callerId; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public Boolean getBillable() { return billable; }
    public void setBillable(Boolean billable) { this.billable = billable; }
    public LocalDateTime getCallTime() { return callTime; }
    public void setCallTime(LocalDateTime callTime) { this.callTime = callTime; }
}
