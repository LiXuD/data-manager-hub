package com.dataplatform.billing.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.api.Result;
import com.dataplatform.billing.entity.BillingPlan;
import com.dataplatform.billing.entity.BillingTemplate;
import com.dataplatform.billing.mapper.BillingPlanMapper;
import com.dataplatform.billing.mapper.BillingPlanTierMapper;
import com.dataplatform.billing.mapper.BillingTemplateMapper;
import com.dataplatform.billing.model.BillingPlanModel;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;

class BillingPlanServiceTest {

    private BillingPlanMapper planMapper;
    private BillingTemplateMapper templateMapper;
    private VendorInternalFeignClient vendorClient;
    private ApiInterfaceFeignClient interfaceClient;
    private VendorConfigInternalFeignClient vendorConfigClient;
    private BillingConfigCodec codec;
    private BillingPlanService service;

    @BeforeEach
    void setUp() {
        planMapper = mock(BillingPlanMapper.class);
        templateMapper = mock(BillingTemplateMapper.class);
        vendorClient = mock(VendorInternalFeignClient.class);
        interfaceClient = mock(ApiInterfaceFeignClient.class);
        vendorConfigClient = mock(VendorConfigInternalFeignClient.class);
        codec = mock(BillingConfigCodec.class);
        service = new BillingPlanService(
                planMapper,
                mock(BillingPlanTierMapper.class),
                templateMapper,
                vendorClient,
                vendorConfigClient,
                interfaceClient,
                codec,
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

    @ParameterizedTest
    @ValueSource(strings = {"PRIMARY", "FALLBACK", "UNASSIGNED"})
    void acceptsAnyUndeletedInterfaceVendorBinding(String routingRole) {
        BillingPlanModel command = enrichmentCommand();
        VendorConfigDTO binding = new VendorConfigDTO();
        binding.setRoutingRole(routingRole);
        stubEnrichment(binding);

        Object enrichment = ReflectionTestUtils.invokeMethod(service, "enrich", command);

        assertEquals("VENDOR-7", command.getVendorCode());
        assertEquals("INTERFACE-11", command.getInterfaceCode());
        assertTrue(enrichment != null);
    }

    @Test
    void rejectsInterfaceVendorWithoutBinding() {
        BillingPlanModel command = enrichmentCommand();
        stubEnrichment(null);
        when(vendorConfigClient.list(eq(7L), eq(null), eq(11L), eq(null)))
                .thenReturn(Result.success(List.of()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "enrich", command));

        assertEquals("所选厂商未绑定到该接口", exception.getMessage());
    }

    @Test
    void failsClosedWhenBindingLookupReturnsNull() {
        BillingPlanModel command = enrichmentCommand();
        stubEnrichment(null);
        when(vendorConfigClient.list(eq(7L), eq(null), eq(11L), eq(null))).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "enrich", command));

        assertEquals("所选厂商未绑定到该接口", exception.getMessage());
    }

    @Test
    void failsClosedWhenBindingLookupReturnsError() {
        BillingPlanModel command = enrichmentCommand();
        stubEnrichment(null);
        when(vendorConfigClient.list(eq(7L), eq(null), eq(11L), eq(null)))
                .thenReturn(Result.error("masterdata unavailable"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "enrich", command));

        assertEquals("所选厂商未绑定到该接口", exception.getMessage());
    }

    private BillingPlanModel enrichmentCommand() {
        BillingPlanModel command = new BillingPlanModel();
        command.setTemplateCode("BASIC");
        command.setVendorId(7L);
        command.setInterfaceId(11L);
        return command;
    }

    private void stubEnrichment(VendorConfigDTO binding) {
        when(templateMapper.selectOne(any())).thenReturn(new BillingTemplate());

        VendorInfoDTO vendor = new VendorInfoDTO();
        vendor.setId(7L);
        vendor.setVendorCode("VENDOR-7");
        vendor.setVendorName("Vendor Seven");
        when(vendorClient.getById(7L)).thenReturn(Result.success(vendor));

        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(11L);
        apiInterface.setInterfaceCode("INTERFACE-11");
        apiInterface.setInterfaceName("Interface Eleven");
        apiInterface.setVendorId(999L);
        when(interfaceClient.getById(11L)).thenReturn(Result.success(apiInterface));

        InterfaceContractDTO contract = new InterfaceContractDTO();
        contract.setResponseFields(List.of());
        when(interfaceClient.getContract(11L)).thenReturn(Result.success(contract));
        when(codec.sha256(any())).thenReturn("fingerprint");

        if (binding != null) {
            when(vendorConfigClient.list(eq(7L), eq(null), eq(11L), eq(null)))
                    .thenReturn(Result.success(List.of(binding)));
        }
    }
}
