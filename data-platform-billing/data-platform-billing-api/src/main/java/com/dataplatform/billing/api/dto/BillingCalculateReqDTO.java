package com.dataplatform.billing.api.dto;

import java.io.Serializable;

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

    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public Integer getCallCount() { return callCount; }
    public void setCallCount(Integer callCount) { this.callCount = callCount; }
    public Long getLatency() { return latency; }
    public void setLatency(Long latency) { this.latency = latency; }
}
