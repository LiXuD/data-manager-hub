package com.dataplatform.billing.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.billing.entity.BillingPlan;
import com.dataplatform.billing.mapper.BillingPlanMapper;
import com.dataplatform.billing.mapper.BillingPlanTierMapper;
import com.dataplatform.billing.mapper.BillingTemplateMapper;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BillingPlanServiceTest {

    private BillingPlanMapper planMapper;
    private BillingPlanService service;

    @BeforeEach
    void setUp() {
        planMapper = mock(BillingPlanMapper.class);
        service = new BillingPlanService(
                planMapper,
                mock(BillingPlanTierMapper.class),
                mock(BillingTemplateMapper.class),
                mock(VendorInternalFeignClient.class),
                mock(ApiInterfaceFeignClient.class),
                mock(BillingConfigCodec.class),
                mock(BillingPlanValidator.class));
    }

    @Test
    void returnsNullWhenNoEffectivePlanExists() {
        when(planMapper.selectEffective(any(), any(), any(), any())).thenReturn(List.of());

        assertNull(service.getEffective("VENDOR", "INTERFACE", LocalDateTime.now()));
    }

    @Test
    void failsLoudlyWhenMultipleEffectivePlansMatch() {
        BillingPlan first = new BillingPlan();
        first.setId(1L);
        BillingPlan second = new BillingPlan();
        second.setId(2L);
        when(planMapper.selectEffective(any(), any(), any(), any()))
                .thenReturn(List.of(first, second));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.getEffective("VENDOR", "INTERFACE", LocalDateTime.now()));

        assertTrue(exception.getMessage().contains("匹配到多个生效版本"));
    }
}
