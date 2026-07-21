package com.dataplatform.billing.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 计费域计费计算的 Billing Rule DTO。
 * <p>跨服务契约数据对象，用于 api 模块暴露远程接口时传递稳定字段。</p>
 */
public class BillingRuleDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long vendorId;
    private String vendorName;
    private Long interfaceId;
    private String interfaceCode;
    private String interfaceName;
    private String dataType;
    private String ruleName;
    private String billingType;
    private BigDecimal unitPrice;
    private Integer tierMin;
    private Integer tierMax;
    private BigDecimal discount;
    private List<BillingTierDTO> tiers;
    private Integer slaThreshold;
    private BigDecimal compensationRate;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public Long getInterfaceId() { return interfaceId; }
    public void setInterfaceId(Long interfaceId) { this.interfaceId = interfaceId; }
    public String getInterfaceCode() { return interfaceCode; }
    public void setInterfaceCode(String interfaceCode) { this.interfaceCode = interfaceCode; }
    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
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
    public List<BillingTierDTO> getTiers() { return tiers; }
    public void setTiers(List<BillingTierDTO> tiers) { this.tiers = tiers; }
    public Integer getSlaThreshold() { return slaThreshold; }
    public void setSlaThreshold(Integer slaThreshold) { this.slaThreshold = slaThreshold; }
    public BigDecimal getCompensationRate() { return compensationRate; }
    public void setCompensationRate(BigDecimal compensationRate) { this.compensationRate = compensationRate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
