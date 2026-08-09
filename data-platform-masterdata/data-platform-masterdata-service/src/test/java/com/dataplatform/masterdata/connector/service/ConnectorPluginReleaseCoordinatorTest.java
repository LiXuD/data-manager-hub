package com.dataplatform.masterdata.connector.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.access.connector.api.feign.ConnectorPluginActivationInternalFeignClient;
import com.dataplatform.api.Result;
import com.dataplatform.masterdata.connector.entity.ConnectorPluginVersion;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import com.dataplatform.masterdata.connector.mapper.ConnectorPluginVersionMapper;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorVersionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConnectorPluginReleaseCoordinatorTest {

    @Test
    void keepsRequiredVersionsAndRetriesPartialReleaseFailuresOnNextReconciliation() {
        VendorConnectorVersionMapper connectorMapper = mock(VendorConnectorVersionMapper.class);
        ConnectorPluginVersionMapper pluginMapper = mock(ConnectorPluginVersionMapper.class);
        ConnectorPluginActivationInternalFeignClient client =
                mock(ConnectorPluginActivationInternalFeignClient.class);
        when(connectorMapper.selectList(any())).thenReturn(List.of(active("required", "1.0.0")));
        when(pluginMapper.selectList(any())).thenReturn(List.of(
                plugin("required", "1.0.0", "https://repo/required.jar"),
                plugin("unused-a", "1.0.0", "https://repo/a.jar"),
                plugin("unused-b", "1.0.0", "https://repo/b.jar"),
                plugin("legacy-http", "1.0.0", "builtin://legacy-http")));
        ConnectorPluginActivationSummaryDTO released = new ConnectorPluginActivationSummaryDTO();
        AtomicInteger aAttempts = new AtomicInteger();
        when(client.release("unused-a", "1.0.0")).thenAnswer(invocation -> {
            if (aAttempts.getAndIncrement() == 0) throw new IllegalStateException("access-1 failed");
            return Result.success(released);
        });
        when(client.release("unused-b", "1.0.0")).thenReturn(Result.success(released));
        ConnectorPluginReleaseCoordinator coordinator = new ConnectorPluginReleaseCoordinator(
                connectorMapper, pluginMapper, client, new ObjectMapper());

        assertThrows(IllegalStateException.class, coordinator::reconcile);
        verify(client).release("unused-b", "1.0.0");
        verify(client, never()).release("required", "1.0.0");
        verify(client, never()).release("legacy-http", "1.0.0");

        assertDoesNotThrow(coordinator::reconcile);
        verify(client, times(2)).release("unused-a", "1.0.0");
    }

    private VendorConnectorVersion active(String pluginId, String version) {
        VendorConnectorVersion connector = new VendorConnectorVersion();
        connector.setStatus("ACTIVE");
        connector.setPipelineSnapshot("[{\"stageKey\":\"transport\",\"capability\":\"TRANSPORT\","
                + "\"pluginId\":\"" + pluginId + "\",\"pluginVersion\":\"" + version + "\","
                + "\"order\":0,\"enabled\":true,\"config\":{},\"configHash\":\"x\"}]");
        return connector;
    }

    private ConnectorPluginVersion plugin(String id, String version, String uri) {
        ConnectorPluginVersion plugin = new ConnectorPluginVersion();
        plugin.setPluginId(id);
        plugin.setVersion(version);
        plugin.setArtifactUri(uri);
        plugin.setStatus("VERIFIED");
        return plugin;
    }
}
