package com.dataplatform.access.connector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.access.connector.api.dto.ConnectorTestPipelineStepDTO;
import com.dataplatform.access.connector.api.dto.VendorConnectorTestReqDTO;
import com.dataplatform.access.connector.api.dto.VendorConnectorTestRespDTO;
import com.dataplatform.api.Result;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.StageTiming;
import com.dataplatform.plugin.spi.TransportStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VendorConnectorControlledTestServiceTest {

    @Test
    void executesDraftDirectlyAndReturnsOnlyRedactedNormalizedResult() {
        VendorConfigInternalFeignClient configClient = mock(VendorConfigInternalFeignClient.class);
        VendorInternalFeignClient vendorClient = mock(VendorInternalFeignClient.class);
        DefaultConnectorVendorExecutor executor = mock(DefaultConnectorVendorExecutor.class);
        VendorConfigDTO config = new VendorConfigDTO();
        config.setId(10L);
        config.setVendorId(20L);
        VendorInfoDTO vendor = new VendorInfoDTO();
        vendor.setVendorCode("DEMO");
        when(configClient.getById(10L)).thenReturn(Result.success(config));
        when(vendorClient.getById(20L)).thenReturn(Result.success(vendor));
        ConnectorExecutionResult execution = new ConnectorExecutionResult(
                TransportStatus.SUCCESS, BusinessStatus.SUCCESS,
                Map.of("name", "Alice", "accessToken", "secret-value"),
                null, null, null, BillingSignal.ELIGIBLE, CacheSignal.CACHEABLE,
                RequestDeliveryState.SENT, "demo", "1.0.0", "draft", "a".repeat(64),
                List.of(new StageTiming("transport", StageCapability.TRANSPORT,
                        "demo", "1.0.0", Duration.ofMillis(12), true)));
        when(executor.executeDraft(config, "DEMO", List.of(), Map.of())).thenReturn(execution);
        VendorConnectorControlledTestService service = new VendorConnectorControlledTestService(
                configClient, vendorClient, executor, new ConnectorTestResultSanitizer());
        VendorConnectorTestReqDTO request = request();

        when(executor.executeDraft(config, "DEMO", request.getPipelineSnapshot(), request.getParams()))
                .thenReturn(execution);
        VendorConnectorTestRespDTO response = service.test(request);

        assertTrue(response.getSuccess());
        assertEquals("Alice", response.getNormalizedData().get("name"));
        assertEquals("***", response.getNormalizedData().get("accessToken"));
        assertEquals(12L, response.getStageTimings().getFirst().getDurationMs());
        verify(executor).executeDraft(config, "DEMO", request.getPipelineSnapshot(), request.getParams());
    }

    private VendorConnectorTestReqDTO request() {
        ConnectorTestPipelineStepDTO step = new ConnectorTestPipelineStepDTO();
        step.setStageKey("transport");
        step.setCapability("TRANSPORT");
        step.setPluginId("demo");
        step.setPluginVersion("1.0.0");
        step.setOrder(1);
        step.setConfigHash("a".repeat(64));
        VendorConnectorTestReqDTO request = new VendorConnectorTestReqDTO();
        request.setVendorConfigId(10L);
        request.setPipelineSnapshot(List.of(step));
        request.setParams(Map.of("id", "1"));
        return request;
    }
}
