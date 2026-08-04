package com.dataplatform.access.connector.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.access.connector.api.dto.ConnectorPluginStageReqDTO;
import com.dataplatform.access.connector.service.ConnectorPluginActivationService;
import org.junit.jupiter.api.Test;

class ConnectorPluginActivationInternalControllerTest {

    @Test
    void delegatesStageWithoutExposingASecondPublicContract() {
        ConnectorPluginActivationService service = mock(ConnectorPluginActivationService.class);
        ConnectorPluginActivationSummaryDTO summary = new ConnectorPluginActivationSummaryDTO();
        summary.setPluginId("demo");
        summary.setPluginVersion("1.0.0");
        summary.setReady(true);
        when(service.requestStage("demo", "1.0.0")).thenReturn(summary);
        ConnectorPluginActivationInternalController controller =
                new ConnectorPluginActivationInternalController(service);
        ConnectorPluginStageReqDTO request = new ConnectorPluginStageReqDTO();
        request.setPluginId("demo");
        request.setPluginVersion("1.0.0");

        var result = controller.stage(request);

        assertEquals(summary, result.getData());
        verify(service).requestStage("demo", "1.0.0");
    }
}
