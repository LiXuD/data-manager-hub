package com.dataplatform.call.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;


public class DataQueryReqVO {
    
    /**
     * 租户ID
     */
    private Long tenantId;
    
    /**
     * 调用方ID
     */
    private Long callerId;
    
    /**
     * 厂商ID
     */
    private Long vendorId;
    
    /**
     * 数据类型ID
     */
    private Long dataTypeId;
    
    /**
     * 查询参数(JSON)
     */
    private String queryParams;
    
    /**
     * 是否使用缓存
     */
    private Boolean useCache = true;

    // Getters and Setters
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getCallerId() { return callerId; }
    public void setCallerId(Long callerId) { this.callerId = callerId; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public Long getDataTypeId() { return dataTypeId; }
    public void setDataTypeId(Long dataTypeId) { this.dataTypeId = dataTypeId; }
    public String getQueryParams() { return queryParams; }
    public void setQueryParams(String queryParams) { this.queryParams = queryParams; }

}