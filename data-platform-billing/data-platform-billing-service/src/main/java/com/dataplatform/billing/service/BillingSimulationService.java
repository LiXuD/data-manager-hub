package com.dataplatform.billing.service;

import com.dataplatform.billing.api.dto.BillingChargeReqDTO;
import com.dataplatform.billing.entity.BillingPlan;
import com.dataplatform.billing.model.BillingPlanModel;
import com.dataplatform.billing.model.BillingSimulationCommand;
import com.dataplatform.billing.model.BillingSimulationResult;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class BillingSimulationService {

    private final BillingPlanService planService;
    private final BillingMeteringEvaluator evaluator;
    private final BillingPricingEngine pricingEngine;

    public BillingSimulationService(BillingPlanService planService,
                                    BillingMeteringEvaluator evaluator,
                                    BillingPricingEngine pricingEngine) {
        this.planService = planService;
        this.evaluator = evaluator;
        this.pricingEngine = pricingEngine;
    }

    public BillingSimulationResult simulate(Long planId, BillingSimulationCommand command) {
        BillingSimulationResult result = new BillingSimulationResult();
        try {
            BillingPlan plan = planService.getEntity(planId);
            BillingPlanModel model = planService.get(planId);
            BillingChargeReqDTO charge = command != null && command.getCharge() != null
                    ? command.getCharge() : new BillingChargeReqDTO();
            BillingMeteringEvaluator.Evaluation evaluation = evaluator.evaluate(model, charge);
            BigDecimal usageBefore = command != null && command.getUsageBefore() != null
                    ? command.getUsageBefore() : BigDecimal.ZERO;
            BillingPricingEngine.PricingResult pricing = evaluation.billable() && !evaluation.pendingReview()
                    ? pricingEngine.calculate(model, evaluation.quantity(), usageBefore, charge)
                    : new BillingPricingEngine.PricingResult(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO);
            result.setValid(!evaluation.pendingReview());
            result.setBillable(evaluation.billable());
            result.setQuantity(pricing.quantity());
            result.setUsageBefore(usageBefore);
            result.setBaseAmount(pricing.baseAmount());
            result.setAdjustmentAmount(pricing.adjustmentAmount());
            result.setFinalAmount(pricing.finalAmount());
            result.setDecisions(evaluation.decisions());
            if (evaluation.pendingReview()) result.getErrors().add("计量字段缺失，模拟结果需要复核");
            if ("TIERED".equals(plan.getTemplateCode())) {
                result.setMatchedTier("累计量 " + usageBefore + " → " + usageBefore.add(evaluation.quantity()));
            }
        } catch (RuntimeException exception) {
            result.setValid(false);
            result.getErrors().add(exception.getMessage());
        }
        return result;
    }
}
