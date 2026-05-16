package com.dataplatform.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("billing_reconciliation")
public class BillingReconciliation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long vendorId;
    private String vendorName;
    private LocalDate billingDate;

    private Long platformCount;
    private BigDecimal platformAmount;

    private Long vendorCount;
    private BigDecimal vendorAmount;

    private Long diffCount;
    private BigDecimal diffAmount;
    private BigDecimal diffRate;

    private String status;  // pending, matched, diff_warning, diff_error
    private String remark;

    private LocalDateTime createdAt;
    private LocalDateTime reconciledAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public LocalDate getBillingDate() { return billingDate; }
    public void setBillingDate(LocalDate billingDate) { this.billingDate = billingDate; }
    public Long getPlatformCount() { return platformCount; }
    public void setPlatformCount(Long platformCount) { this.platformCount = platformCount; }
    public BigDecimal getPlatformAmount() { return platformAmount; }
    public void setPlatformAmount(BigDecimal platformAmount) { this.platformAmount = platformAmount; }
    public Long getVendorCount() { return vendorCount; }
    public void setVendorCount(Long vendorCount) { this.vendorCount = vendorCount; }
    public BigDecimal getVendorAmount() { return vendorAmount; }
    public void setVendorAmount(BigDecimal vendorAmount) { this.vendorAmount = vendorAmount; }
    public Long getDiffCount() { return diffCount; }
    public void setDiffCount(Long diffCount) { this.diffCount = diffCount; }
    public BigDecimal getDiffAmount() { return diffAmount; }
    public void setDiffAmount(BigDecimal diffAmount) { this.diffAmount = diffAmount; }
    public BigDecimal getDiffRate() { return diffRate; }
    public void setDiffRate(BigDecimal diffRate) { this.diffRate = diffRate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(LocalDateTime reconciledAt) { this.reconciledAt = reconciledAt; }
}