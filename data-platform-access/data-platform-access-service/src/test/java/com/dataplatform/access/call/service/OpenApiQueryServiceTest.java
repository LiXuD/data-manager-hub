package com.dataplatform.access.call.service;

import com.dataplatform.access.call.service.OpenApiQueryService.OpenApiCallContext;
import com.dataplatform.access.call.service.VendorProxyService;
import com.dataplatform.access.call.vo.OpenApiQueryRespVO;
import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.BillingCalculateReqDTO;
import com.dataplatform.billing.api.dto.BillingCalculateRespDTO;
import com.dataplatform.billing.api.dto.BillingChargeReqDTO;
import com.dataplatform.billing.api.dto.BillingChargeRespDTO;
import com.dataplatform.billing.api.dto.BillingMeteringPolicyDTO;
import com.dataplatform.billing.api.feign.BillingInternalFeignClient;
import com.dataplatform.common.entity.CallRecord;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(callRecordService.findLatestReusableCache(eq("PERSONAL_QUERY"), anyString(), eq(20L),
                any(LocalDateTime.class), eq("GLOBAL"))).thenReturn(cachedRecord);
        OpenApiQueryRespVO response = service.query(buildContext(true, 3));

        assertTrue(response.getSuccess());
        assertTrue(response.getCached());
        assertEquals(100L, response.getCacheSourceRecordId());
        assertEquals(BigDecimal.ZERO, response.getCost());
        assertEquals(99, response.getData().get("score"));
        verify(vendorProxyService, never()).callVendor(anyString(), anyString(), any(), any());

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
    }

    @Test
    void shouldRecordResponseContractWarningWithoutFailingCall() {
        CallRecord cachedRecord = new CallRecord();
        cachedRecord.setId(101L);
        cachedRecord.setResponseData("{\"success\":true,\"data\":{\"score\":\"invalid\"}}");
        when(callRecordService.findLatestReusableCache(eq("PERSONAL_QUERY"), anyString(), eq(20L),
                any(LocalDateTime.class), eq("GLOBAL"))).thenReturn(cachedRecord);
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
        ArgumentCaptor<CallRecord> recordCaptor = ArgumentCaptor.forClass(CallRecord.class);
        verify(callRecordEventPublisher).publish(recordCaptor.capture());
        assertFalse(recordCaptor.getValue().getResponseContractValid());
        assertNotNull(recordCaptor.getValue().getResponseContractErrors());
        assertTrue(recordCaptor.getValue().getResponseContractErrors().contains("score"));
    }

    @Test
    void shouldRejectNonObjectResponseRootEvenWhenAllConfiguredFieldsAreOptional() {
        CallRecord cachedRecord = new CallRecord();
        cachedRecord.setId(102L);
        cachedRecord.setResponseData("{\"success\":true,\"data\":\"unexpected-root\"}");
        when(callRecordService.findLatestReusableCache(eq("PERSONAL_QUERY"), anyString(), eq(20L),
                any(LocalDateTime.class), eq("GLOBAL"))).thenReturn(cachedRecord);
        InterfaceParamDTO optionalScore = new InterfaceParamDTO();
        optionalScore.setParamName("score");
        optionalScore.setParamType("integer");
        optionalScore.setRequired(false);
        InterfaceContractDTO contract = new InterfaceContractDTO();
        contract.setResponseFields(java.util.List.of(optionalScore));
        OpenApiCallContext context = buildContext(true, 3);
        context.setInterfaceContract(contract);

        OpenApiQueryRespVO response = service.query(context);

        assertTrue(response.getSuccess());
        ArgumentCaptor<CallRecord> recordCaptor = ArgumentCaptor.forClass(CallRecord.class);
        verify(callRecordEventPublisher).publish(recordCaptor.capture());
        assertFalse(recordCaptor.getValue().getResponseContractValid());
        assertTrue(recordCaptor.getValue().getResponseContractErrors().contains("data类型必须为object"));
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
