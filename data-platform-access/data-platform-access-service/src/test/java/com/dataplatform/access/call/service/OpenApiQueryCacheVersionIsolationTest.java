package com.dataplatform.access.call.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.dataplatform.access.call.service.OpenApiQueryService.OpenApiCallContext;
import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.BillingChargeRespDTO;
import com.dataplatform.billing.api.dto.BillingMeteringPolicyDTO;
import com.dataplatform.billing.api.feign.BillingInternalFeignClient;
import com.dataplatform.common.entity.CallRecord;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class OpenApiQueryCacheVersionIsolationTest {

    @Test
    void cacheHashPinsInterfaceVersionAndCanonicalizesParameterOrder() {
        OpenApiQueryService service = new OpenApiQueryService(
                mock(CallRecordService.class), mock(CallRecordEventPublisher.class),
                mock(VendorProxyService.class), mock(BillingInternalFeignClient.class),
                new BillingFactExtractor());
        LinkedHashMap<String, Object> firstOrder = new LinkedHashMap<>();
        firstOrder.put("name", "alice");
        firstOrder.put("age", 30);
        LinkedHashMap<String, Object> reverseOrder = new LinkedHashMap<>();
        reverseOrder.put("age", 30);
        reverseOrder.put("name", "alice");

        String v1 = hash(service, context("v1", firstOrder));
        String v1Reordered = hash(service, context("v1", reverseOrder));
        String defaultVersion = hash(service, context(null, firstOrder));
        String v2 = hash(service, context("v2", firstOrder));

        assertEquals(v1, v1Reordered);
        assertEquals(v1, defaultVersion);
        assertNotEquals(v1, v2);
    }

    @Test
    void normalizedPluginResultStillPassesThroughTheResponseContractGate() {
        CallRecordService records = mock(CallRecordService.class);
        CallRecordEventPublisher publisher = mock(CallRecordEventPublisher.class);
        VendorProxyService vendorProxy = mock(VendorProxyService.class);
        BillingInternalFeignClient billing = mock(BillingInternalFeignClient.class);
        BillingMeteringPolicyDTO policy = new BillingMeteringPolicyDTO();
        policy.setPlanId(1L);
        policy.setPlanVersion(2);
        policy.setPolicyHash("policy-hash");
        when(billing.getMeteringPolicy(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(Result.success(policy));
        BillingChargeRespDTO charge = new BillingChargeRespDTO();
        charge.setFinalAmount(BigDecimal.ZERO);
        when(billing.charge(any())).thenReturn(Result.success(charge));
        when(vendorProxy.callVendor(anyString(), anyString(), any(), any())).thenReturn(new LinkedHashMap<>(Map.of(
                "success", true,
                "data", Map.of("score", "not-an-integer"),
                "pluginId", "vendor-http",
                "pluginVersion", "1.2.0",
                "pipelineVersion", 7,
                "snapshotHash", "a".repeat(64))));
        OpenApiQueryService service = new OpenApiQueryService(
                records, publisher, vendorProxy, billing, new BillingFactExtractor());
        OpenApiCallContext context = context("v1", Map.of("id", "1"));
        context.setApiCode("PERSON_QUERY");
        context.setVendorCode("vendor-a");
        context.setDataTypeCode("person");
        context.setUseCache(false);
        InterfaceParamDTO score = new InterfaceParamDTO();
        score.setParamName("score");
        score.setParamType("integer");
        score.setRequired(true);
        InterfaceContractDTO contract = new InterfaceContractDTO();
        contract.setResponseFields(List.of(score));
        context.setInterfaceContract(contract);

        com.dataplatform.access.call.vo.OpenApiQueryRespVO response = service.query(context);

        ArgumentCaptor<CallRecord> captured = ArgumentCaptor.forClass(CallRecord.class);
        verify(publisher).publish(captured.capture());
        assertEquals("vendor-http", captured.getValue().getPluginId());
        assertEquals(7, captured.getValue().getPipelineVersion());
        assertFalse(captured.getValue().getResponseContractValid());
        assertFalse(captured.getValue().getSuccess());
        assertEquals("CONTRACT_VIOLATION", captured.getValue().getErrorCode());
        assertFalse(response.getSuccess());
        assertEquals("CONTRACT_VIOLATION", response.getErrorCode());
        verify(billing, never()).charge(any());
    }

    private String hash(OpenApiQueryService service, OpenApiCallContext context) {
        return ReflectionTestUtils.invokeMethod(service, "buildRequestHash", context);
    }

    private OpenApiCallContext context(String apiVersion, Map<String, Object> params) {
        OpenApiCallContext context = new OpenApiCallContext();
        context.setApiVersion(apiVersion);
        context.setParams(params);
        return context;
    }
}
