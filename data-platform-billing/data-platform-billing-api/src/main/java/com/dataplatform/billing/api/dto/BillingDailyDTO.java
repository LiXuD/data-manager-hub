package com.dataplatform.billing.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 计费域计费计算的 Billing Daily DTO。
 * <p>跨服务契约数据对象，用于 api 模块暴露远程接口时传递稳定字段。</p>
 */
public class BillingDailyDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long callerId;
    private Long vendorId;
    private LocalDate billingDate;
    private Integer totalCalls;
    private Integer successCalls;
    private Integer failedCalls;
    private BigDecimal totalCost;
    private BigDecimal totalRevenue;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getCallerId() { return callerId; }
    public void setCallerId(Long callerId) { this.callerId = callerId; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public LocalDate getBillingDate() { return billingDate; }
    public void setBillingDate(LocalDate billingDate) { this.billingDate = billingDate; }
    public Integer getTotalCalls() { return totalCalls; }
    public void setTotalCalls(Integer totalCalls) { this.totalCalls = totalCalls; }
    public Integer getSuccessCalls() { return successCalls; }
    public void setSuccessCalls(Integer successCalls) { this.successCalls = successCalls; }
    public Integer getFailedCalls() { return failedCalls; }
    public void setFailedCalls(Integer failedCalls) { this.failedCalls = failedCalls; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
