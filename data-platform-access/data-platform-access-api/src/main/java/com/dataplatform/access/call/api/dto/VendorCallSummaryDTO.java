package com.dataplatform.access.call.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public class VendorCallSummaryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long callCount;
    private BigDecimal totalAmount;

    public Long getCallCount() { return callCount; }
    public void setCallCount(Long callCount) { this.callCount = callCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
}
