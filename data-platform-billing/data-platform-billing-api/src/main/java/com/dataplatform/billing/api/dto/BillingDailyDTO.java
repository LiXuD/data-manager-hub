package com.dataplatform.billing.api.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
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
}
