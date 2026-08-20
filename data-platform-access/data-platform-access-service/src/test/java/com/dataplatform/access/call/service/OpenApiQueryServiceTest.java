package com.dataplatform.access.call.service;

import com.dataplatform.access.call.service.OpenApiQueryService.OpenApiCallContext;
import com.dataplatform.access.call.service.VendorProxyService;
import com.dataplatform.access.call.vo.OpenApiQueryRespVO;
import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.BillingChargeReqDTO;
import com.dataplatform.billing.api.dto.BillingChargeRespDTO;
import com.dataplatform.billing.api.dto.BillingMeteringPolicyDTO;
import com.dataplatform.billing.api.feign.BillingInternalFeignClient;
import com.dataplatform.common.entity.CallRecord;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.StageTiming;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenApiQueryServiceTest {

    private CallRecordService callRecordService;
    private CallRecordEventPublisher callRecordEventPublisher;
    private VendorProxyService vendorProxyService;
    private BillingInternalFeignClient billingFeignClient;
    private OpenApiQueryService service;
    private BillingFactExtractor billingFactExtractor;

    @BeforeEach
    void setUp() {
        callRecordService = mock(CallRecordService.class);
        callRecordEventPublisher = mock(CallRecordEventPublisher.class);
        vendorProxyService = mock(VendorProxyService.class);
        billingFeignClient = mock(BillingInternalFeignClient.class);
        billingFactExtractor = new BillingFactExtractor();
        service = new OpenApiQueryService(callRecordService, callRecordEventPublisher,
                vendorProxyService, billingFeignClient, billingFactExtractor);
        BillingMeteringPolicyDTO policy = new BillingMeteringPolicyDTO();
        policy.setPlanId(1L);
        policy.setPlanVersion(1);
        policy.setPolicyHash("policy-hash");
        when(billingFeignClient.getMeteringPolicy(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(Result.success(policy));
        BillingChargeRespDTO charge = new BillingChargeRespDTO();
        charge.setFinalAmount(BigDecimal.ZERO);
        when(billingFeignClient.charge(any())).thenReturn(Result.success(charge));
    }

    @Test
    void shouldReturnHistoricalRecordWhenCacheHit() {
        CallRecord cachedRecord = new CallRecord();
        cachedRecord.setId(100L);
        cachedRecord.setResponseData("{\"success\":true,\"data\":{\"score\":99}}");
        cachedRecord.setPluginId("vendor-http");
        cachedRecord.setPluginVersion("1.2.0");
        cachedRecord.setPipelineVersion(7);
        cachedRecord.setSnapshotHash("a".repeat(64));
        cachedRecord.setHashAlgorithm("V2_EMBEDDED");
        cachedRecord.setIntegrityHash("a".repeat(64));
        when(callRecordService.findLatestReusableCache(eq("PERSONAL_QUERY"), anyString(), eq(1L), eq(20L),
                any(LocalDateTime.class), eq("GLOBAL"))).thenReturn(cachedRecord);
        OpenApiQueryRespVO response = service.query(buildContext(true, 3));

        assertTrue(response.getSuccess());
        assertTrue(response.getCached());
        assertEquals(100L, response.getCacheSourceRecordId());
        assertEquals(BigDecimal.ZERO, response.getCost());
        assertEquals(99, response.getData().get("score"));
        verify(vendorProxyService, never()).callVendor(anyString(), anyString(), any(), any(), anyString());

        ArgumentCaptor<CallRecord> recordCaptor = ArgumentCaptor.forClass(CallRecord.class);
        verify(callRecordEventPublisher).publish(recordCaptor.capture());
        CallRecord savedRecord = recordCaptor.getValue();
        assertTrue(savedRecord.getCacheHit());
        assertEquals(BigDecimal.ZERO, savedRecord.getCost());
        assertEquals(100L, savedRecord.getCacheSourceRecordId());
        assertEquals("trace-1", savedRecord.getTraceId());

        ArgumentCaptor<BillingChargeReqDTO> billingCaptor =
                ArgumentCaptor.forClass(BillingChargeReqDTO.class);
        verify(billingFeignClient).charge(billingCaptor.capture());
        assertEquals("vendor-a", billingCaptor.getValue().getVendorCode());
        assertEquals("PERSONAL_QUERY", billingCaptor.getValue().getInterfaceCode());
        assertEquals("personal", billingCaptor.getValue().getDataType());
        assertEquals("vendor-http", billingCaptor.getValue().getPluginId());
        assertEquals("1.2.0", billingCaptor.getValue().getPluginVersion());
        assertEquals(7, billingCaptor.getValue().getPipelineVersion());
        assertEquals("a".repeat(64), billingCaptor.getValue().getSnapshotHash());
        assertEquals("V2_EMBEDDED", billingCaptor.getValue().getHashAlgorithm());
        assertEquals("a".repeat(64), billingCaptor.getValue().getIntegrityHash());
    }

    @Test
    void shouldPersistCacheableResultContainingDurationStageTimings() {
        when(vendorProxyService.callVendor(anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new java.util.LinkedHashMap<>(Map.of(
                        "success", true,
                        "data", Map.of("score", 99),
                        "billingSignal", "ELIGIBLE",
                        "cacheSignal", "CACHEABLE",
                        "deliveryState", "SENT",
                        "actualVendorCode", "vendor-a",
                        "stageTimings", java.util.List.of(new StageTiming(
                                "transport", StageCapability.TRANSPORT, "legacy-http", "1.0.0",
                                Duration.ofMillis(12), true)))));

        OpenApiQueryRespVO response = service.query(buildContext(true, 3));

        assertTrue(response.getSuccess());
        ArgumentCaptor<CallRecord> recordCaptor = ArgumentCaptor.forClass(CallRecord.class);
        verify(callRecordEventPublisher).publish(recordCaptor.capture());
        assertFalse("{}".equals(recordCaptor.getValue().getResponseData()));
        assertTrue(recordCaptor.getValue().getResponseData().contains("stageTimings"));
        assertTrue(recordCaptor.getValue().getResponseData().contains("transport"));
    }

    @Test
    void shouldTurnInvalidResponseContractIntoNonBillableFailure() {
        when(vendorProxyService.callVendor(anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new java.util.LinkedHashMap<>(Map.of(
                        "success", true,
                        "data", Map.of("score", "invalid"),
                        "actualVendorId", 40L,
                        "actualVendorCode", "vendor-a",
                        "billingSignal", "ELIGIBLE",
                        "cacheSignal", "CACHEABLE",
                        "deliveryState", "SENT")));
        InterfaceParamDTO score = new InterfaceParamDTO();
        score.setParamName("score");
        score.setParamType("integer");
        score.setRequired(true);
        InterfaceContractDTO contract = new InterfaceContractDTO();
        contract.setResponseFields(java.util.List.of(score));
        OpenApiCallContext context = buildContext(false, 3);
        context.setInterfaceContract(contract);

        OpenApiQueryRespVO response = service.query(context);

        assertFalse(response.getSuccess());
        assertEquals("CONTRACT_VIOLATION", response.getErrorCode());
        assertEquals(BigDecimal.ZERO, response.getCost());
        verify(billingFeignClient, never()).charge(any());
        ArgumentCaptor<CallRecord> recordCaptor = ArgumentCaptor.forClass(CallRecord.class);
        verify(callRecordEventPublisher).publish(recordCaptor.capture());
        assertFalse(recordCaptor.getValue().getResponseContractValid());
        assertFalse(recordCaptor.getValue().getSuccess());
        assertFalse(recordCaptor.getValue().getUseCache());
        assertNotNull(recordCaptor.getValue().getResponseContractErrors());
        assertTrue(recordCaptor.getValue().getResponseContractErrors().contains("score"));
    }

    @Test
    void shouldRejectNonObjectResponseRootEvenWhenAllConfiguredFieldsAreOptional() {
        Map<String, Object> vendorResult = new java.util.LinkedHashMap<>();
        vendorResult.put("success", true);
        vendorResult.put("data", "unexpected-root");
        vendorResult.put("billingSignal", "ELIGIBLE");
        vendorResult.put("cacheSignal", "CACHEABLE");
        when(vendorProxyService.callVendor(anyString(), anyString(), any(), any(), anyString())).thenReturn(vendorResult);
        InterfaceParamDTO optionalScore = new InterfaceParamDTO();
        optionalScore.setParamName("score");
        optionalScore.setParamType("integer");
        optionalScore.setRequired(false);
        InterfaceContractDTO contract = new InterfaceContractDTO();
        contract.setResponseFields(java.util.List.of(optionalScore));
        OpenApiCallContext context = buildContext(false, 3);
        context.setInterfaceContract(contract);

        OpenApiQueryRespVO response = service.query(context);

        assertFalse(response.getSuccess());
        assertEquals("CONTRACT_VIOLATION", response.getErrorCode());
        verify(billingFeignClient, never()).charge(any());
        ArgumentCaptor<CallRecord> recordCaptor = ArgumentCaptor.forClass(CallRecord.class);
        verify(callRecordEventPublisher).publish(recordCaptor.capture());
        assertFalse(recordCaptor.getValue().getResponseContractValid());
        assertTrue(recordCaptor.getValue().getResponseContractErrors().contains("data类型必须为object"));
    }

    @Test
    void cachedResponseThatViolatesCurrentContractIsTreatedAsMiss() {
        CallRecord cachedRecord = new CallRecord();
        cachedRecord.setId(102L);
        cachedRecord.setResponseContractValid(true);
        cachedRecord.setResponseData("{\"success\":true,\"data\":{\"score\":\"stale\"}}");
        when(callRecordService.findLatestReusableCache(eq("PERSONAL_QUERY"), anyString(), eq(1L), eq(20L),
                any(LocalDateTime.class), eq("GLOBAL"))).thenReturn(cachedRecord);
        when(vendorProxyService.callVendor(anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new java.util.LinkedHashMap<>(Map.of(
                        "success", true,
                        "data", Map.of("score", 88),
                        "actualVendorId", 40L,
                        "actualVendorCode", "vendor-a",
                        "billingSignal", "ELIGIBLE",
                        "cacheSignal", "CACHEABLE")));
        InterfaceParamDTO score = new InterfaceParamDTO();
        score.setParamName("score");
        score.setParamType("integer");
        score.setRequired(true);
        InterfaceContractDTO contract = new InterfaceContractDTO();
        contract.setResponseFields(java.util.List.of(score));
        OpenApiCallContext context = buildContext(true, 3);
        context.setInterfaceContract(contract);

        OpenApiQueryRespVO response = service.query(context);

        assertTrue(response.getSuccess());
        assertFalse(response.getCached());
        assertEquals(88, response.getData().get("score"));
        verify(vendorProxyService).callVendor(anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void shouldUseIndependentCacheWindowsForDifferentCallers() {
        CallRecord cachedRecord = new CallRecord();
        cachedRecord.setId(103L);
        cachedRecord.setResponseData("{\"success\":true,\"data\":{\"score\":99}}");
        when(callRecordService.findLatestReusableCache(eq("PERSONAL_QUERY"), anyString(), eq(1L), any(),
                any(LocalDateTime.class), eq("CALLER"))).thenReturn(cachedRecord);

        OpenApiCallContext riskContext = buildContext(true, 2);
        riskContext.setCallerId(20L);
        riskContext.setCacheScope("CALLER");
        service.query(riskContext);

        OpenApiCallContext backOfficeContext = buildContext(true, 10);
        backOfficeContext.setCallerId(21L);
        backOfficeContext.setCacheScope("CALLER");
        service.query(backOfficeContext);

        ArgumentCaptor<Long> callerCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(callRecordService, times(2)).findLatestReusableCache(
                eq("PERSONAL_QUERY"), anyString(), eq(1L), callerCaptor.capture(),
                sinceCaptor.capture(), eq("CALLER"));
        assertEquals(java.util.List.of(20L, 21L), callerCaptor.getAllValues());
        long windowDifferenceHours = Duration.between(
                sinceCaptor.getAllValues().get(1), sinceCaptor.getAllValues().get(0)).toHours();
        assertTrue(windowDifferenceHours >= 191 && windowDifferenceHours <= 193);
        verify(vendorProxyService, never()).callVendor(anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void shouldPersistActualPluginAndFallbackVendorTrace() {
        Map<String, Object> pluginResult = new java.util.LinkedHashMap<>();
        pluginResult.put("success", true);
        pluginResult.put("data", Map.of("score", 88));
        pluginResult.put("actualVendorId", 41L);
        pluginResult.put("actualVendorCode", "vendor-b");
        pluginResult.put("pluginId", "vendor-http");
        pluginResult.put("pluginVersion", "1.2.0");
        pluginResult.put("pipelineVersion", "7");
        pluginResult.put("snapshotHash", "a".repeat(64));
        pluginResult.put("hashAlgorithm", "V2_EMBEDDED");
        pluginResult.put("integrityHash", "a".repeat(64));
        pluginResult.put("billingSignal", "ELIGIBLE");
        pluginResult.put("cacheSignal", "NOT_CACHEABLE");
        when(vendorProxyService.callVendor(anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(pluginResult);

        OpenApiQueryRespVO response = service.query(buildContext(true, 3));

        assertTrue(response.getSuccess());
        ArgumentCaptor<CallRecord> recordCaptor = ArgumentCaptor.forClass(CallRecord.class);
        verify(callRecordEventPublisher).publish(recordCaptor.capture());
        CallRecord record = recordCaptor.getValue();
        assertEquals(41L, record.getVendorId());
        assertEquals("vendor-b", record.getVendorCode());
        assertEquals("vendor-http", record.getPluginId());
        assertEquals("1.2.0", record.getPluginVersion());
        assertEquals(7, record.getPipelineVersion());
        assertEquals("a".repeat(64), record.getSnapshotHash());
        assertEquals("V2_EMBEDDED", record.getHashAlgorithm());
        assertEquals("a".repeat(64), record.getIntegrityHash());
        assertFalse(record.getUseCache());
        ArgumentCaptor<String> pluginRequestId = ArgumentCaptor.forClass(String.class);
        verify(vendorProxyService).callVendor(anyString(), anyString(), any(), any(),
                pluginRequestId.capture());
        assertEquals(response.getPlatformRequestId(), pluginRequestId.getValue());
        assertEquals(response.getPlatformRequestId(), record.getRequestId());
        ArgumentCaptor<BillingChargeReqDTO> billingCaptor =
                ArgumentCaptor.forClass(BillingChargeReqDTO.class);
        verify(billingFeignClient).charge(billingCaptor.capture());
        assertTrue(billingCaptor.getValue().getSuccess());
        assertEquals(41L, billingCaptor.getValue().getVendorId());
        assertEquals("vendor-b", billingCaptor.getValue().getVendorCode());
        assertEquals("vendor-http", billingCaptor.getValue().getPluginId());
        assertEquals("1.2.0", billingCaptor.getValue().getPluginVersion());
        assertEquals(7, billingCaptor.getValue().getPipelineVersion());
        assertEquals("a".repeat(64), billingCaptor.getValue().getSnapshotHash());
        assertEquals("V2_EMBEDDED", billingCaptor.getValue().getHashAlgorithm());
        assertEquals("a".repeat(64), billingCaptor.getValue().getIntegrityHash());
        assertEquals(response.getPlatformRequestId(), billingCaptor.getValue().getRequestId());
        verify(billingFeignClient).getMeteringPolicy(eq("vendor-b"), eq("PERSONAL_QUERY"), any(LocalDateTime.class));
    }

    @Test
    void successWithoutExplicitBillingOrCacheEligibilityFailsClosed() {
        when(vendorProxyService.callVendor(anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new java.util.LinkedHashMap<>(Map.of(
                        "success", true,
                        "data", Map.of("score", 88),
                        "billingSignal", "UNKNOWN",
                        "cacheSignal", "UNKNOWN")));

        OpenApiQueryRespVO response = service.query(buildContext(true, 3));

        assertTrue(response.getSuccess());
        assertEquals(BigDecimal.ZERO, response.getCost());
        verify(billingFeignClient, never()).charge(any());
        ArgumentCaptor<CallRecord> recordCaptor = ArgumentCaptor.forClass(CallRecord.class);
        verify(callRecordEventPublisher).publish(recordCaptor.capture());
        assertFalse(recordCaptor.getValue().getUseCache());
    }

    @Test
    void explicitFailureBillingEvidenceUsesActualVendorButKeepsFailureFact() {
        when(vendorProxyService.callVendor(anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new java.util.LinkedHashMap<>(Map.of(
                        "success", false,
                        "data", Map.of("decision", "rejected"),
                        "errorCode", "BUSINESS_REJECTED",
                        "actualVendorId", 41L,
                        "actualVendorCode", "vendor-b",
                        "billingSignal", "ELIGIBLE",
                        "cacheSignal", "NOT_CACHEABLE")));

        OpenApiQueryRespVO response = service.query(buildContext(true, 3));

        assertFalse(response.getSuccess());
        ArgumentCaptor<BillingChargeReqDTO> billingCaptor =
                ArgumentCaptor.forClass(BillingChargeReqDTO.class);
        verify(billingFeignClient).charge(billingCaptor.capture());
        assertFalse(billingCaptor.getValue().getSuccess());
        assertEquals(41L, billingCaptor.getValue().getVendorId());
        assertEquals("vendor-b", billingCaptor.getValue().getVendorCode());
        assertFalse(billingCaptor.getValue().getResponseContractValid());
        verify(billingFeignClient).getMeteringPolicy(
                eq("vendor-b"), eq("PERSONAL_QUERY"), any(LocalDateTime.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRecursivelyMaskSecretsInRecordedCollections() {
        Map<String, Object> source = new java.util.LinkedHashMap<>();
        source.put("items", java.util.List.of(
                Map.of("token", "top-secret", "safe", "visible"),
                Map.of("nested", Map.of("password", "hidden"))));

        Map<String, Object> sanitized = ReflectionTestUtils.invokeMethod(service, "sanitizeForRecord", source);

        java.util.List<Map<String, Object>> items = (java.util.List<Map<String, Object>>) sanitized.get("items");
        assertEquals("***MASKED***", items.get(0).get("token"));
        assertEquals("visible", items.get(0).get("safe"));
        assertEquals("***MASKED***", ((Map<String, Object>) items.get(1).get("nested")).get("password"));
    }

    private OpenApiCallContext buildContext(boolean useCache, Integer cacheDays) {
        OpenApiCallContext context = new OpenApiCallContext();
        context.setExternalRequestId("client-req-1");
        context.setTraceId("trace-1");
        context.setApiCode("PERSONAL_QUERY");
        context.setApiVersion("v1");
        context.setTenantId(1L);
        context.setCallerId(20L);
        context.setApiKeyId(10L);
        context.setVendorId(40L);
        context.setVendorCode("vendor-a");
        context.setDataTypeCode("personal");
        context.setProductId(60L);
        context.setProductCode("loan-risk");
        context.setProductName("信贷风控");
        context.setSceneCode("pre-loan-review");
        context.setSceneName("贷前审批");
        context.setUseCache(useCache);
        context.setCacheDays(cacheDays);
        context.setCacheScope("GLOBAL");
        context.setParams(Map.of("name", "zhangsan"));
        return context;
    }
}
