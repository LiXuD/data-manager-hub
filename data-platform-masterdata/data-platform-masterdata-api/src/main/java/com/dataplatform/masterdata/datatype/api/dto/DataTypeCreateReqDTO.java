package com.dataplatform.masterdata.datatype.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;
import java.math.BigDecimal;

public class DataTypeCreateReqDTO implements Serializable {

    @NotBlank(message = "数据类型编码不能为空")
    private String dataTypeCode;

    @NotBlank(message = "数据类型名称不能为空")
    private String dataTypeName;

    private String dataCategory;
    private String description;
    private String pricingModel;
    private BigDecimal unitPrice;
    private String status;

    public String getDataTypeCode() { return dataTypeCode; }
    public void setDataTypeCode(String dataTypeCode) { this.dataTypeCode = dataTypeCode; }
    public String getDataTypeName() { return dataTypeName; }
    public void setDataTypeName(String dataTypeName) { this.dataTypeName = dataTypeName; }
    public String getDataCategory() { return dataCategory; }
    public void setDataCategory(String dataCategory) { this.dataCategory = dataCategory; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPricingModel() { return pricingModel; }
    public void setPricingModel(String pricingModel) { this.pricingModel = pricingModel; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
