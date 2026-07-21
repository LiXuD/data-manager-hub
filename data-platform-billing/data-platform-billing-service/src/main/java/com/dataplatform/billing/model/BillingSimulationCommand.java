package com.dataplatform.billing.model;

import com.dataplatform.billing.api.dto.BillingChargeReqDTO;
import java.math.BigDecimal;

/** 计费方案模拟输入。 */

public class BillingSimulationCommand {
    private BillingChargeReqDTO charge = new BillingChargeReqDTO();
    private BigDecimal usageBefore = BigDecimal.ZERO;

    public BillingChargeReqDTO getCharge() { return charge; }
    public void setCharge(BillingChargeReqDTO charge) { this.charge = charge; }
    public BigDecimal getUsageBefore() { return usageBefore; }
    public void setUsageBefore(BigDecimal usageBefore) { this.usageBefore = usageBefore; }
}
