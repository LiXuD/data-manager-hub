package com.dataplatform.billing.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

        BillingPlanException exception = assertThrows(BillingPlanException.class,
                () -> service.getEffective("VENDOR", "INTERFACE", LocalDateTime.now()));

        assertTrue(exception.getMessage().contains("匹配到多个生效版本"));
        assertEquals("BILLING_PLAN_DATA_CONFLICT", exception.getErrorCode());
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

        BillingPlanException exception = assertThrows(BillingPlanException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "enrich", command));

        assertEquals(400, exception.getStatus());
        assertEquals("BILLING_PLAN_INVALID", exception.getErrorCode());
    }

    @Test
    void failsClosedWhenBindingLookupReturnsNull() {
        BillingPlanModel command = enrichmentCommand();
        stubEnrichment(null);
        when(vendorConfigClient.list(eq(7L), eq(null), eq(11L), eq(null))).thenReturn(null);

        BillingPlanException exception = assertThrows(BillingPlanException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "enrich", command));

        assertEquals(503, exception.getStatus());
        assertEquals("BILLING_DEPENDENCY_UNAVAILABLE", exception.getErrorCode());
    }

    @Test
    void failsClosedWhenBindingLookupReturnsError() {
        BillingPlanModel command = enrichmentCommand();
        stubEnrichment(null);
        when(vendorConfigClient.list(eq(7L), eq(null), eq(11L), eq(null)))
                .thenReturn(Result.error("masterdata unavailable"));

        BillingPlanException exception = assertThrows(BillingPlanException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "enrich", command));

        assertEquals(503, exception.getStatus());
        assertEquals("BILLING_DEPENDENCY_UNAVAILABLE", exception.getErrorCode());
    }

    @Test
    void rejectsMissingEnrichmentReferencesBeforeCallingDependencies() {
        BillingPlanModel command = new BillingPlanModel();

        BillingPlanException exception = assertThrows(BillingPlanException.class,
                () -> service.createDraft(command));

        assertEquals(400, exception.getStatus());
        assertEquals("BILLING_PLAN_VALIDATION_FAILED", exception.getErrorCode());
        verifyNoInteractions(templateMapper, vendorClient, interfaceClient, vendorConfigClient, planMapper);
    }

    @Test
    void mapsContractDependencyFailureToServiceUnavailableDuringValidation() {
        BillingPlan candidate = storedPlan(2L, "PLAN-B", "DRAFT", "2026-09-10T00:00:00");
        when(planMapper.selectById(2L)).thenReturn(candidate);
        when(interfaceClient.getContract(11L)).thenThrow(new IllegalStateException("dependency down"));

        BillingPlanException exception = assertThrows(BillingPlanException.class,
                () -> service.validate(2L));

        assertEquals(503, exception.getStatus());
        assertEquals("BILLING_DEPENDENCY_UNAVAILABLE", exception.getErrorCode());
    }

    @Test
    void doesNotCallContractDependencyWhenPublishKeyIsIncomplete() {
        BillingPlan candidate = storedPlan(2L, "PLAN-B", "DRAFT", "2026-09-10T00:00:00");
        candidate.setVendorId(null);
        when(planMapper.selectById(2L)).thenReturn(candidate);

        List<String> errors = service.validate(2L);

        assertTrue(errors.stream().anyMatch(error -> error.contains("厂商不能为空")));
        verify(interfaceClient, never()).getContract(any());
    }

    @Test
    void missingPlanIsReportedAsNotFoundBeforePublishLocking() {
        when(planMapper.selectByIdForUpdate(404L)).thenReturn(null);

        BillingPlanException exception = assertThrows(BillingPlanException.class,
                () -> service.publish(404L));

        assertEquals(404, exception.getStatus());
        assertEquals("BILLING_PLAN_NOT_FOUND", exception.getErrorCode());
        verify(planMapper, never()).ensurePublishLock(any(), any(), any());
    }

    @Test
    void overlappingDifferentPlanIsConflictAndDoesNotCloseExistingVersion() {
        BillingPlan candidate = storedPlan(2L, "PLAN-B", "DRAFT", "2026-09-10T00:00:00");
        BillingPlan existing = storedPlan(1L, "PLAN-A", "ACTIVE", "2026-09-01T00:00:00");
        existing.setEffectiveTo(LocalDateTime.parse("2026-10-01T00:00:00"));
        when(planMapper.selectByIdForUpdate(2L)).thenReturn(candidate);
        when(planMapper.selectPublishableForUpdate(7L, 11L, "VENDOR_PAYABLE"))
                .thenReturn(List.of(existing));
        stubPublishModel(candidate);

        BillingPlanException exception = assertThrows(BillingPlanException.class,
                () -> service.publish(2L));

        assertEquals(409, exception.getStatus());
        assertEquals("BILLING_PLAN_EFFECTIVE_OVERLAP", exception.getErrorCode());
        verify(planMapper, never()).updateById(any(BillingPlan.class));
    }

    @Test
    void publishSucceedsOnlyAfterSerializedPreflight() {
        BillingPlan candidate = storedPlan(2L, "PLAN-B", "DRAFT", "2026-09-10T00:00:00");
        when(planMapper.selectByIdForUpdate(2L)).thenReturn(candidate);
        when(planMapper.selectPublishableForUpdate(7L, 11L, "VENDOR_PAYABLE"))
                .thenReturn(List.of());
        stubPublishModel(candidate);
        when(planMapper.selectById(2L)).thenReturn(candidate);
        when(planMapper.updateById(candidate)).thenReturn(1);

        BillingPlanModel result = service.publish(2L);

        assertEquals(2L, result.getId());
        verify(planMapper).ensurePublishLock(7L, 11L, "VENDOR_PAYABLE");
        verify(planMapper).lockPublishKey(7L, 11L, "VENDOR_PAYABLE");
        verify(planMapper).updateById(candidate);
    }

    @Test
    void validateReportsPublishConflictBeforeSubmit() {
        BillingPlan candidate = storedPlan(2L, "PLAN-B", "DRAFT", "2026-09-10T00:00:00");
        BillingPlan existing = storedPlan(1L, "PLAN-A", "ACTIVE", "2026-09-01T00:00:00");
        existing.setEffectiveTo(LocalDateTime.parse("2026-10-01T00:00:00"));
        when(planMapper.selectById(2L)).thenReturn(candidate);
        when(planMapper.selectPublishable(7L, 11L, "VENDOR_PAYABLE")).thenReturn(List.of(existing));
        stubPublishModel(candidate);

        List<String> errors = service.validate(2L);

        assertTrue(errors.stream().anyMatch(error -> error.contains("生效区间重叠")));
        verify(planMapper, never()).selectPublishableForUpdate(any(), any(), any());
    }

    private BillingPlan storedPlan(Long id, String planCode, String status, String effectiveFrom) {
        BillingPlan plan = new BillingPlan();
        plan.setId(id);
        plan.setPlanCode(planCode);
        plan.setVersion(1);
        plan.setPlanName(planCode);
        plan.setVendorId(7L);
        plan.setInterfaceId(11L);
        plan.setTemplateCode("PER_CALL");
        plan.setAccountingPurpose("VENDOR_PAYABLE");
        plan.setCurrency("CNY");
        plan.setTimezone("Asia/Shanghai");
        plan.setSettlementCycle("MONTH");
        plan.setStatus(status);
        plan.setEffectiveFrom(LocalDateTime.parse(effectiveFrom));
        plan.setPricingConfig("{}");
        plan.setMeteringConfig("{}");
        plan.setAdjustmentConfig("{}");
        return plan;
    }

    private void stubPublishModel(BillingPlan candidate) {
        BillingPlanModel model = new BillingPlanModel();
        model.setId(candidate.getId());
        model.setPlanCode(candidate.getPlanCode());
        model.setPlanName(candidate.getPlanName());
        model.setVendorId(candidate.getVendorId());
        model.setInterfaceId(candidate.getInterfaceId());
        model.setTemplateCode(candidate.getTemplateCode());
        model.setAccountingPurpose(candidate.getAccountingPurpose());
        model.setCurrency(candidate.getCurrency());
        model.setTimezone(candidate.getTimezone());
        model.setSettlementCycle(candidate.getSettlementCycle());
        model.setEffectiveFrom(candidate.getEffectiveFrom());
        model.setEffectiveTo(candidate.getEffectiveTo());
        when(codec.toModel(eq(candidate), any())).thenReturn(model);
        when(codec.sha256(any())).thenReturn("fingerprint");
        when(planMapper.lockPublishKey(7L, 11L, "VENDOR_PAYABLE")).thenReturn(7L);
        when(interfaceClient.getContract(11L)).thenReturn(Result.success(new InterfaceContractDTO()));
        when(mockValidator().validate(any(), any())).thenReturn(List.of());
    }

    private BillingPlanValidator mockValidator() {
        return (BillingPlanValidator) ReflectionTestUtils.getField(service, "validator");
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
