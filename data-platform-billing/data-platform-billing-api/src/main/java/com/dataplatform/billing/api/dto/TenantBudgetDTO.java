package com.dataplatform.billing.api.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class TenantBudgetDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private BigDecimal monthlyBudget;
    private BigDecimal usedAmount;
    private BigDecimal remainingAmount;
    private String alertThreshold;
    private Integer status;
}
