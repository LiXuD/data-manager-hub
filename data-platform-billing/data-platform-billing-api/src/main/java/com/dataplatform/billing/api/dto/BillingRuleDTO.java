package com.dataplatform.billing.api.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class BillingRuleDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String ruleName;
    private String billingType;
    private BigDecimal unitPrice;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Integer status;
}
