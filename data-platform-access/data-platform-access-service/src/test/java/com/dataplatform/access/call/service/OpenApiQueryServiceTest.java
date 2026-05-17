package com.dataplatform.access.call.service;

import com.dataplatform.access.call.service.OpenApiQueryService.OpenApiCallContext;
import com.dataplatform.access.call.service.VendorProxyService;
import com.dataplatform.access.call.vo.OpenApiQueryRespVO;
import com.dataplatform.billing.api.feign.BillingFeignClient;
import com.dataplatform.common.entity.CallRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenApiQueryServiceTest {

    private CallRecordService callRecordService;
    private VendorProxyService vendorProxyService;
    private BillingFeignClient billingFeignClient;
    private OpenApiQueryService service;

    @BeforeEach
    void setUp() {
        callRecordService = mock(CallRecordService.class);
        vendorProxyService = mock(VendorProxyService.class);
        billingFeignClient = mock(BillingFeignClient.class);
        service = new OpenApiQueryService(callRecordService, vendorProxyService, billingFeignClient);
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
        verify(callRecordService).save(recordCaptor.capture());
        CallRecord savedRecord = recordCaptor.getValue();
        assertTrue(savedRecord.getCacheHit());
        assertEquals(BigDecimal.ZERO, savedRecord.getCost());
        assertEquals(100L, savedRecord.getCacheSourceRecordId());
    }

    private OpenApiCallContext buildContext(boolean useCache, Integer cacheDays) {
        OpenApiCallContext context = new OpenApiCallContext();
        context.setExternalRequestId("client-req-1");
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
