package com.dataplatform.billing.service.impl;

import com.dataplatform.billing.entity.BillingRule;
import com.dataplatform.billing.mapper.BillingRuleMapper;
import com.dataplatform.common.billing.BillingCalculatorFactory;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceImplTest {

    @Mock
    private BillingRuleMapper billingRuleMapper;

    @Spy
    private BillingCalculatorFactory billingCalculatorFactory = new BillingCalculatorFactory();

    @InjectMocks
    private BillingServiceImpl billingService;

    @Test
    void shouldHonorStandardBillingTypeWithoutTierOrSlaAdjustments() {
        BillingRule rule = rule("STANDARD");
        rule.setDiscount(new BigDecimal("0.50"));
        rule.setSlaThreshold(100);
        rule.setCompensationRate(new BigDecimal("0.50"));
        when(billingRuleMapper.selectOne(any())).thenReturn(rule);

        BigDecimal cost = billingService.calculateCost("personal", 2, 5000L);

        assertEquals(new BigDecimal("20.0000"), cost);
    }

    @Test
    void shouldDelegateTieredBillingToSharedCalculator() {
        BillingRule rule = rule("TIERED");
        rule.setDiscount(new BigDecimal("0.50"));
        when(billingRuleMapper.selectOne(any())).thenReturn(rule);

        BigDecimal cost = billingService.calculateCost("personal", 2, 0L);

        assertEquals(new BigDecimal("10.0000"), cost);
    }

    private BillingRule rule(String billingType) {
        BillingRule rule = new BillingRule();
        rule.setBillingType(billingType);
        rule.setDataType("personal");
        rule.setUnitPrice(new BigDecimal("10.00"));
        rule.setTierMin(0);
        rule.setStatus("active");
        return rule;
    }
}
