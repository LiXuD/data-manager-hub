package com.dataplatform.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("billing_rule")
public class BillingRule {
    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonProperty("ruleName")
    private String ruleName;

    @JsonProperty("vendorId")
    private Long vendorId;

    @JsonProperty("vendorName")
    private String vendorName;

    @JsonProperty("dataType")
    private String dataType;

    @JsonProperty("billingType")
    private String billingType = "STANDARD";

    @JsonProperty("unitPrice")
    private BigDecimal unitPrice;

    public void setPricePerUnit(BigDecimal price) {
        if (this.unitPrice == null && price != null) {
            this.unitPrice = price;
        }
    }

    @JsonProperty("tierMin")
    private Integer tierMin;

    @JsonProperty("tierMax")
    private Integer tierMax;

    @JsonProperty("discount")
    private BigDecimal discount;

    // SLA相关字段
    @JsonProperty("slaThreshold")
    private Integer slaThreshold;          // SLA阈值(毫秒)

    @JsonProperty("compensationRate")
    private BigDecimal compensationRate;   // 补偿系数

    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getBillingType() { return billingType; }
    public void setBillingType(String billingType) { this.billingType = billingType; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public Integer getTierMin() { return tierMin; }
    public void setTierMin(Integer tierMin) { this.tierMin = tierMin; }
    public Integer getTierMax() { return tierMax; }
    public void setTierMax(Integer tierMax) { this.tierMax = tierMax; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public Integer getSlaThreshold() { return slaThreshold; }
    public void setSlaThreshold(Integer slaThreshold) { this.slaThreshold = slaThreshold; }
    public BigDecimal getCompensationRate() { return compensationRate; }
    public void setCompensationRate(BigDecimal compensationRate) { this.compensationRate = compensationRate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}