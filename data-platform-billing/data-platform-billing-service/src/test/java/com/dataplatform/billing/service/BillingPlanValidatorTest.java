package com.dataplatform.billing.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.billing.model.BillingPlanModel;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class BillingPlanValidatorTest {

    private final BillingPlanValidator validator = new BillingPlanValidator();

    @Test
    void acceptsCompletePerItemPlanBoundToContractField() {
        BillingPlanModel plan = basePlan();
        plan.setTemplateCode("PER_ITEM");
        plan.getPricing().setUnitPrice(new BigDecimal("0.1"));
        plan.getMetering().getQuantity().setType("ARRAY_SIZE");
        plan.getMetering().getQuantity().setFieldId(10L);
        plan.getMetering().getQuantity().setPath("$.data.items");
        plan.getMetering().getQuantity().setExtraction("ARRAY_SIZE");
        plan.getMetering().getQuantity().setUnit("ITEM");

        assertTrue(validator.validate(plan, contract(arrayField(10L, "items"))).isEmpty());
    }

    @Test
    void rejectsFieldOutsideContractAndFieldIdPathMismatch() {
        BillingPlanModel plan = basePlan();
        BillingPlanModel.ConditionConfig condition = new BillingPlanModel.ConditionConfig();
        condition.setAlias("status");
        condition.setFieldId(11L);
        condition.setPath("$.data.unknown");
        condition.setOperator("EQ");
        condition.setExpectedValue("OK");
        plan.getMetering().setConditions(List.of(condition));

        List<String> errors = validator.validate(plan, contract(scalarField(11L, "status", "string")));

        assertTrue(errors.stream().anyMatch(error -> error.contains("不在当前接口响应契约")));
    }

    @Test
    void rejectsGappedTierAndFiniteLastTier() {
        BillingPlanModel plan = basePlan();
        plan.setTemplateCode("TIERED");
        plan.getPricing().setUnitPrice(BigDecimal.ONE);
        plan.setTiers(List.of(tier("0", "100"), tier("120", "200")));

        List<String> errors = validator.validate(plan, contract());

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.contains("连续")));
    }

    @Test
    void durationTemplateCannotUseFixedQuantity() {
        BillingPlanModel plan = basePlan();
        plan.setTemplateCode("DURATION");
        plan.getPricing().setUnitPrice(BigDecimal.ONE);
        assertTrue(validator.validate(plan, contract()).stream()
                .anyMatch(error -> error.contains("必须是DURATION")));
    }

    private BillingPlanModel basePlan() {
        BillingPlanModel plan = new BillingPlanModel();
        plan.setPlanName("test");
        plan.setTemplateCode("PER_CALL");
        plan.setVendorId(1L);
        plan.setInterfaceId(2L);
        plan.setAccountingPurpose("VENDOR_PAYABLE");
        plan.setCurrency("CNY");
        plan.setTimezone("Asia/Shanghai");
        plan.setSettlementCycle("MONTH");
        plan.setEffectiveFrom(LocalDateTime.of(2026, 1, 1, 0, 0));
        plan.getPricing().setUnitPrice(BigDecimal.ONE);
        return plan;
    }

    private InterfaceContractDTO contract(InterfaceParamDTO... fields) {
        InterfaceContractDTO contract = new InterfaceContractDTO();
        contract.setResponseFields(List.of(fields));
        return contract;
    }

    private InterfaceParamDTO arrayField(Long id, String name) {
        return scalarField(id, name, "array");
    }

    private InterfaceParamDTO scalarField(Long id, String name, String type) {
        InterfaceParamDTO field = new InterfaceParamDTO();
        field.setId(id);
        field.setParamName(name);
        field.setParamType(type);
        return field;
    }

    private BillingPlanModel.TierConfig tier(String min, String max) {
        BillingPlanModel.TierConfig tier = new BillingPlanModel.TierConfig();
        tier.setTierMin(new BigDecimal(min));
        tier.setTierMax(max != null ? new BigDecimal(max) : null);
        tier.setDiscount(BigDecimal.ONE);
        return tier;
    }
}
