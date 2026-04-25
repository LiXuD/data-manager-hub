package com.dataplatform.common.entity.unified;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 统一计费规则实体
 */
@TableName("billing_rule")
public class BillingRuleDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleName;
    private Long vendorId;
    private String vendorName;
    private String dataType;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 阶梯下限 */
    private Integer tierMin;

    /** 阶梯上限 */
    private Integer tierMax;

    /** 折扣率 */
    private BigDecimal discount;

    /** SLA阈值(毫秒) */
    private Integer slaThreshold;

    /** SLA补偿系数 */
    private BigDecimal compensationRate;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    // Getters and Setters
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
