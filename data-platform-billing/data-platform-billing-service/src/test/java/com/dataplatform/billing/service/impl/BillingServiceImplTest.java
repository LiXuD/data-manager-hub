package com.dataplatform.billing.service.impl;

import com.dataplatform.billing.entity.BillingRule;
import com.dataplatform.billing.entity.BillingRuleTier;
import com.dataplatform.billing.mapper.BillingRuleMapper;
import com.dataplatform.billing.mapper.BillingRuleTierMapper;
import com.dataplatform.billing.mapper.BillingTierUsageMapper;
import com.dataplatform.api.Result;
import com.dataplatform.common.billing.BillingCalculatorFactory;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class BillingServiceImplTest {

    @Mock
    private BillingRuleMapper billingRuleMapper;

    @Mock
    private BillingRuleTierMapper billingRuleTierMapper;

    @Mock
    private BillingTierUsageMapper billingTierUsageMapper;

    @Mock
    private VendorInternalFeignClient vendorInternalFeignClient;

    @Mock
    private ApiInterfaceFeignClient apiInterfaceFeignClient;

    @Spy
    private BillingCalculatorFactory billingCalculatorFactory = new BillingCalculatorFactory();

    @InjectMocks
    private BillingServiceImpl billingService;

    @BeforeEach
    void setUp() {
        VendorInfoDTO vendor = new VendorInfoDTO();
        vendor.setId(9L);
        vendor.setVendorCode("vendor-a");
        vendor.setVendorName("厂商A");
        lenient().when(vendorInternalFeignClient.getByVendorCode("vendor-a"))
                .thenReturn(Result.success(vendor));
    }

    @Test
    void shouldHonorStandardBillingTypeWithoutTierOrSlaAdjustments() {
        BillingRule rule = rule("STANDARD");
        rule.setDiscount(new BigDecimal("0.50"));
        rule.setSlaThreshold(100);
        rule.setCompensationRate(new BigDecimal("0.50"));
        when(billingRuleMapper.selectOne(any())).thenReturn(rule);

        BigDecimal cost = billingService.calculateCost("vendor-a", "INTERFACE_A", 2, 5000L);

        assertEquals(new BigDecimal("20.0000"), cost);
    }

    @Test
    void shouldDelegateTieredBillingToSharedCalculator() {
        BillingRule rule = rule("TIERED");
        rule.setUnitPrice(new BigDecimal("0.01"));
        rule.setTiers(List.of(
                tier(0, 100_000L, "1.00"),
                tier(100_000L, 200_000L, "0.90"),
                tier(200_000L, 500_000L, "0.80"),
                tier(500_000L, null, "0.70")
        ));
        when(billingRuleMapper.selectOne(any())).thenReturn(rule);

        BigDecimal cost = billingService.calculateCost("vendor-a", "INTERFACE_A", 600_000, 0L);

        assertEquals(new BigDecimal("5000.0000"), cost);
    }

    @Test
    void shouldPriceEachRequestByMonthlyAccumulatedTierAndKeepRetriesIdempotent() {
        BillingRule rule = rule("TIERED");
        rule.setId(88L);
        List<BillingRuleTier> tiers = List.of(
                tier(0, 2L, "1.00"),
                tier(2, null, "0.50")
        );
        when(billingRuleMapper.selectOne(any())).thenReturn(rule);
        when(billingRuleTierMapper.selectList(any())).thenReturn(tiers);
        when(billingTierUsageMapper.selectUsageBeforeByRequestId("req-tier"))
                .thenReturn(null, 1L);
        when(billingTierUsageMapper.selectCurrentUsage(anyLong(), any())).thenReturn(1L);

        BigDecimal firstCost = billingService.calculateCost(
                "vendor-a", "INTERFACE_A", 2, 0L, "req-tier",
                java.time.LocalDate.of(2026, 7, 20));
        BigDecimal retryCost = billingService.calculateCost(
                "vendor-a", "INTERFACE_A", 2, 0L, "req-tier",
                java.time.LocalDate.of(2026, 7, 20));

        assertEquals(new BigDecimal("15.0000"), firstCost);
        assertEquals(firstCost, retryCost);
        verify(billingTierUsageMapper, times(1))
                .incrementUsage(88L, java.time.LocalDate.of(2026, 7, 1), 2);
        verify(billingTierUsageMapper, times(1))
                .insertUsageEvent("req-tier", 88L,
                        java.time.LocalDate.of(2026, 7, 1), 1L, 2L);
    }

    @Test
    void shouldRejectTieredRuntimeCalculationWithoutRequestId() {
        BillingRule rule = rule("TIERED");
        rule.setId(88L);
        when(billingRuleMapper.selectOne(any())).thenReturn(rule);
        when(billingRuleTierMapper.selectList(any())).thenReturn(List.of(
                tier(0, null, "1.00")));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> billingService.calculateCost(
                        "vendor-a", "INTERFACE_A", 1, 0L, null,
                        java.time.LocalDate.of(2026, 7, 20)));

        assertEquals("阶梯计费请求必须提供不超过64位的requestId", exception.getMessage());
    }

    @Test
    void shouldBindRuleToUniqueVendorInterface() {
        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(12L);
        apiInterface.setVendorId(9L);
        apiInterface.setInterfaceCode("INTERFACE_A");
        apiInterface.setInterfaceName("接口A");
        VendorInfoDTO vendor = new VendorInfoDTO();
        vendor.setId(9L);
        vendor.setVendorName("厂商A");
        when(apiInterfaceFeignClient.getById(12L)).thenReturn(Result.success(apiInterface));
        when(vendorInternalFeignClient.getById(9L)).thenReturn(Result.success(vendor));
        when(billingRuleMapper.selectCount(any())).thenReturn(0L);

        BillingRule rule = rule("STANDARD");
        rule.setVendorId(9L);
        rule.setInterfaceId(12L);

        billingService.saveRule(rule);

        assertEquals("INTERFACE_A", rule.getInterfaceCode());
        assertEquals("接口A", rule.getInterfaceName());
        assertEquals("厂商A", rule.getVendorName());
        verify(billingRuleMapper).insert(rule);
    }

    @Test
    void shouldRejectDuplicateVendorInterfaceRule() {
        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(12L);
        apiInterface.setVendorId(9L);
        apiInterface.setInterfaceCode("INTERFACE_A");
        VendorInfoDTO vendor = new VendorInfoDTO();
        vendor.setId(9L);
        when(apiInterfaceFeignClient.getById(anyLong())).thenReturn(Result.success(apiInterface));
        when(vendorInternalFeignClient.getById(9L)).thenReturn(Result.success(vendor));
        when(billingRuleMapper.selectCount(any())).thenReturn(1L);

        BillingRule rule = rule("STANDARD");
        rule.setVendorId(9L);
        rule.setInterfaceId(12L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> billingService.saveRule(rule));
        assertEquals("该厂商与接口已存在计费规则", exception.getMessage());
    }

    @Test
    void shouldPersistMultipleTiersInsideOneVendorInterfaceRule() {
        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(12L);
        apiInterface.setVendorId(9L);
        apiInterface.setInterfaceCode("INTERFACE_A");
        VendorInfoDTO vendor = new VendorInfoDTO();
        vendor.setId(9L);
        when(apiInterfaceFeignClient.getById(12L)).thenReturn(Result.success(apiInterface));
        when(vendorInternalFeignClient.getById(9L)).thenReturn(Result.success(vendor));
        when(billingRuleMapper.selectCount(any())).thenReturn(0L);

        BillingRule rule = rule("TIERED");
        rule.setId(88L);
        rule.setVendorId(9L);
        rule.setInterfaceId(12L);
        rule.setTiers(List.of(
                tier(0, 100_000L, "1.00"),
                tier(100_000L, null, "0.90")
        ));

        billingService.saveRule(rule);

        verify(billingRuleMapper).insert(rule);
        verify(billingRuleTierMapper, times(2)).insert(any(BillingRuleTier.class));
    }

    @Test
    void shouldRejectTierRangesWithGaps() {
        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(12L);
        apiInterface.setVendorId(9L);
        apiInterface.setInterfaceCode("INTERFACE_A");
        VendorInfoDTO vendor = new VendorInfoDTO();
        vendor.setId(9L);
        when(apiInterfaceFeignClient.getById(12L)).thenReturn(Result.success(apiInterface));
        when(vendorInternalFeignClient.getById(9L)).thenReturn(Result.success(vendor));
        when(billingRuleMapper.selectCount(any())).thenReturn(0L);

        BillingRule rule = rule("TIERED");
        rule.setVendorId(9L);
        rule.setInterfaceId(12L);
        rule.setTiers(List.of(
                tier(0, 100_000L, "1.00"),
                tier(200_000L, null, "0.80")
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> billingService.saveRule(rule));
        assertEquals("阶梯区间必须从0开始且连续无间隔", exception.getMessage());
    }

    private BillingRule rule(String billingType) {
        BillingRule rule = new BillingRule();
        rule.setBillingType(billingType);
        rule.setInterfaceCode("INTERFACE_A");
        rule.setUnitPrice(new BigDecimal("10.00"));
        rule.setTierMin(0);
        rule.setStatus("active");
        return rule;
    }

    private BillingRuleTier tier(long min, Long max, String discount) {
        BillingRuleTier tier = new BillingRuleTier();
        tier.setTierMin(min);
        tier.setTierMax(max);
        tier.setDiscount(new BigDecimal(discount));
        return tier;
    }
}
