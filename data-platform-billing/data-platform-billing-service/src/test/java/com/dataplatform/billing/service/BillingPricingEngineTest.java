package com.dataplatform.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dataplatform.billing.api.dto.BillingChargeReqDTO;
import com.dataplatform.billing.model.BillingPlanModel;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BillingPricingEngineTest {

    private final BillingPricingEngine engine = new BillingPricingEngine();

    @Test
    void perCallUsesQuantityAndEightDecimalMoneyScale() {
        BillingPlanModel plan = plan("PER_CALL", "0.125");
        BillingPricingEngine.PricingResult result = engine.calculate(
                plan, new BigDecimal("3"), BigDecimal.ZERO, request(false, 20));
        assertAmount("0.37500000", result.finalAmount());
    }

    @Test
    void tieredPricingChargesOnlyIncrementAcrossBoundary() {
        BillingPlanModel plan = plan("TIERED", "1");
        plan.setTiers(List.of(tier("0", "100", "1"), tier("100", null, "0.8")));

        BillingPricingEngine.PricingResult result = engine.calculate(
                plan, new BigDecimal("20"), new BigDecimal("90"), request(false, 10));

        assertAmount("18.00000000", result.finalAmount());
    }

    @Test
    void packageCountChargesOnlyNewOverage() {
        BillingPlanModel plan = plan("PACKAGE_COUNT", "0");
        plan.getPricing().setIncludedUnits(new BigDecimal("100"));
        plan.getPricing().setOverageUnitPrice(new BigDecimal("0.5"));

        BillingPricingEngine.PricingResult result = engine.calculate(
                plan, new BigDecimal("20"), new BigDecimal("90"), request(false, 10));

        assertAmount("5.00000000", result.finalAmount());
    }

    @Test
    void durationRoundsUpAndSlaNeverMakesAmountNegative() {
        BillingPlanModel plan = plan("DURATION", "2");
        plan.getPricing().setDurationUnit("SECOND");
        plan.getPricing().setDurationRounding("CEILING");
        plan.getAdjustment().setSlaEnabled(true);
        plan.getAdjustment().setSlaThresholdMs(100L);
        plan.getAdjustment().setCompensationRatePer100Ms(new BigDecimal("0.25"));

        BillingPricingEngine.PricingResult result = engine.calculate(
                plan, new BigDecimal("1500"), BigDecimal.ZERO, request(false, 600));

        assertAmount("0.00000000", result.finalAmount());
        assertEquals(0, new BigDecimal("2").compareTo(result.quantity()));
    }

    @Test
    void customCachePriceIsUsedOnlyForCachedResponse() {
        BillingPlanModel plan = plan("PER_CALL", "1");
        plan.getMetering().setCacheBillingPolicy("CUSTOM");
        plan.getPricing().setCacheUnitPrice(new BigDecimal("0.2"));
        BillingPricingEngine.PricingResult result = engine.calculate(
                plan, BigDecimal.ONE, BigDecimal.ZERO, request(true, 1));
        assertAmount("0.20000000", result.finalAmount());
    }

    private BillingPlanModel plan(String template, String unitPrice) {
        BillingPlanModel plan = new BillingPlanModel();
        plan.setTemplateCode(template);
        plan.getPricing().setUnitPrice(new BigDecimal(unitPrice));
        return plan;
    }

    private BillingPlanModel.TierConfig tier(String min, String max, String price) {
        BillingPlanModel.TierConfig tier = new BillingPlanModel.TierConfig();
        tier.setTierMin(new BigDecimal(min));
        tier.setTierMax(max != null ? new BigDecimal(max) : null);
        tier.setUnitPrice(new BigDecimal(price));
        tier.setDiscount(BigDecimal.ONE);
        return tier;
    }

    private BillingChargeReqDTO request(boolean cached, long latency) {
        BillingChargeReqDTO request = new BillingChargeReqDTO();
        request.setCached(cached);
        request.setLatencyMs(latency);
        return request;
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
