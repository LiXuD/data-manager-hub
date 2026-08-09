package com.dataplatform.masterdata.connector.service;

import com.dataplatform.access.connector.api.dto.VendorConnectorTestReqDTO;
import com.dataplatform.access.connector.api.dto.VendorConnectorTestRespDTO;
import com.dataplatform.access.connector.api.feign.VendorConnectorRuntimeInternalFeignClient;
import com.dataplatform.api.Result;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorRuntimeSnapshotDTO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveVendorConnectorRuntimeServiceTest {

    @Mock
    private VendorConnectorService connectorService;
    @Mock
    private VendorConnectorRuntimeInternalFeignClient runtimeClient;

    @Test
    void shouldPinPublishedSnapshotWhenExecutingManagementQuery() {
        ConnectorPipelineStepDTO transport = new ConnectorPipelineStepDTO(
                "transport", "TRANSPORT", "legacy-http", "1.0.0", 20, true,
                Map.of(), "config-hash");
        VendorConnectorRuntimeSnapshotDTO snapshot = new VendorConnectorRuntimeSnapshotDTO(
                7L, 11L, 4, "snapshot-hash", 2, "ACTIVE", List.of(transport), LocalDateTime.now());
        VendorConnectorTestRespDTO response = new VendorConnectorTestRespDTO();
        response.setSuccess(true);
        response.setNormalizedData(Map.of("value", "ok"));
        when(connectorService.runtimeSnapshot(7L)).thenReturn(snapshot);
        when(runtimeClient.test(org.mockito.ArgumentMatchers.any())).thenReturn(Result.success(response));

        ActiveVendorConnectorRuntimeService service =
                new ActiveVendorConnectorRuntimeService(connectorService, runtimeClient);
        Map<String, Object> result = service.execute(7L, Map.of("id", "A-1"));

        assertEquals(true, result.get("success"));
        assertEquals(4, result.get("pipelineVersion"));
        assertEquals("snapshot-hash", result.get("snapshotHash"));
        ArgumentCaptor<VendorConnectorTestReqDTO> request =
                ArgumentCaptor.forClass(VendorConnectorTestReqDTO.class);
        verify(runtimeClient).test(request.capture());
        assertEquals(7L, request.getValue().getVendorConfigId());
        assertEquals("config-hash", request.getValue().getPipelineSnapshot().get(0).getConfigHash());
        assertEquals(Map.of("id", "A-1"), request.getValue().getParams());
    }

    @Test
    void shouldFailClosedWhenNoPublishedSnapshotExists() {
        when(connectorService.runtimeSnapshot(8L)).thenReturn(null);
        ActiveVendorConnectorRuntimeService service =
                new ActiveVendorConnectorRuntimeService(connectorService, runtimeClient);

        Map<String, Object> result = service.execute(8L, Map.of());

        assertEquals(false, result.get("success"));
        assertEquals("CONNECTOR_NOT_PUBLISHED", result.get("errorCode"));
    }
}
