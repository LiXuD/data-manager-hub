package com.dataplatform.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.billing.api.dto.BillingChargeReqDTO;
import com.dataplatform.billing.model.BillingPlanModel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BillingMeteringEvaluatorTest {

    private final BillingMeteringEvaluator evaluator = new BillingMeteringEvaluator();

    @Test
    void chargesOnlyWhenAllResponseConditionsMatch() {
        BillingPlanModel plan = plan();
        plan.getMetering().setConditions(List.of(
                condition("status", "EQ", "OK"),
                condition("count", "GTE", 2)));
        BillingChargeReqDTO request = request();
        request.setMeteringFacts(Map.of("status", "OK", "count", 3, "quantity", 3));
        plan.getMetering().getQuantity().setType("FACT");

        BillingMeteringEvaluator.Evaluation result = evaluator.evaluate(plan, request);

        assertTrue(result.billable());
        assertFalse(result.pendingReview());
        assertEquals(0, new BigDecimal("3").compareTo(result.quantity()));
    }

    @Test
    void missingFieldDefaultsToPendingReviewWithoutCharging() {
        BillingPlanModel plan = plan();
        plan.getMetering().setConditions(List.of(condition("status", "EQ", "OK")));

        BillingMeteringEvaluator.Evaluation result = evaluator.evaluate(plan, request());

        assertFalse(result.billable());
        assertTrue(result.pendingReview());
        assertEquals(BigDecimal.ZERO, result.quantity());
    }

    @Test
    void cacheFreeAndFailedCallShortCircuitBeforeFacts() {
        BillingPlanModel plan = plan();
        BillingChargeReqDTO cached = request();
        cached.setCached(true);
        assertFalse(evaluator.evaluate(plan, cached).billable());

        BillingChargeReqDTO failed = request();
        failed.setSuccess(false);
        assertFalse(evaluator.evaluate(plan, failed).billable());
    }

    @Test
    void errorPolicyRejectsMissingFinancialEvidence() {
        BillingPlanModel plan = plan();
        plan.getMetering().setMissingFieldPolicy("ERROR");
        plan.getMetering().setConditions(List.of(condition("status", "EQ", "OK")));

        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(plan, request()));
    }

    @Test
    void invalidQuantityCannotCreateNegativeUsage() {
        BillingPlanModel plan = plan();
        plan.getMetering().getQuantity().setType("FACT");
        BillingChargeReqDTO request = request();
        request.setMeteringFacts(Map.of("quantity", -1));

        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(plan, request));
    }

    private BillingPlanModel plan() {
        BillingPlanModel plan = new BillingPlanModel();
        plan.setTemplateCode("PER_CALL");
        plan.getMetering().getQuantity().setAlias("quantity");
        return plan;
    }

    private BillingChargeReqDTO request() {
        BillingChargeReqDTO request = new BillingChargeReqDTO();
        request.setSuccess(true);
        request.setCached(false);
        request.setResponseContractValid(true);
        return request;
    }

    private BillingPlanModel.ConditionConfig condition(String alias, String operator, Object expected) {
        BillingPlanModel.ConditionConfig condition = new BillingPlanModel.ConditionConfig();
        condition.setAlias(alias);
        condition.setOperator(operator);
        condition.setExpectedValue(expected);
        return condition;
    }
}
