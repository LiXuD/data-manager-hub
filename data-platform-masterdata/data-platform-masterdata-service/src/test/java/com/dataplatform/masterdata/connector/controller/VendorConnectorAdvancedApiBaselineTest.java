package com.dataplatform.masterdata.connector.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPipelineStepDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorDraftDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorPublishRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorRollbackRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorSaveDraftRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorTestRequestDTO;
import com.dataplatform.masterdata.connector.service.VendorConnectorService;
import com.dataplatform.masterdata.connector.service.ConnectorLegacyWriteRetiredException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class VendorConnectorAdvancedApiBaselineTest {

    @Test
    void freezesCurrentAdvancedPipelineRoutesAndRawDraftShape() throws Exception {
        assertArrayEquals(new String[]{"/vendor/config"},
                VendorConnectorController.class.getAnnotation(RequestMapping.class).value());
        assertGet("active", new Class<?>[]{Long.class}, "/{configId}/connector");
        assertGet("draft", new Class<?>[]{Long.class}, "/{configId}/connector/draft");
        assertPut("saveDraft", new Class<?>[]{Long.class, VendorConnectorSaveDraftRequestDTO.class},
                "/{configId}/connector/draft");
        assertPost("validate", new Class<?>[]{Long.class}, "/{configId}/connector/validate");
        assertPost("test", new Class<?>[]{Long.class, VendorConnectorTestRequestDTO.class},
                "/{configId}/connector/test");
        assertPost("publish", new Class<?>[]{Long.class, VendorConnectorPublishRequestDTO.class},
                "/{configId}/connector/publish");
        assertGet("versions", new Class<?>[]{Long.class}, "/{configId}/connector/versions");
        assertPost("rollback", new Class<?>[]{Long.class, Integer.class,
                        VendorConnectorRollbackRequestDTO.class},
                "/{configId}/connector/rollback/{version}");

        assertArrayEquals(new String[]{"expectedDraftVersion", "pipelineSnapshot"},
                Arrays.stream(VendorConnectorSaveDraftRequestDTO.class.getRecordComponents())
                        .map(component -> component.getName()).toArray(String[]::new));
        assertArrayEquals(new String[]{"stageKey", "capability", "pluginId", "pluginVersion",
                        "order", "enabled", "config", "configHash", "artifactSha256",
                        "manifestHash", "schemaHash"},
                Arrays.stream(ConnectorPipelineStepDTO.class.getRecordComponents())
                        .map(component -> component.getName()).toArray(String[]::new));
    }

    @Test
    void rawDraftWriteKeepsCasAndDelegatesTheUnchangedPipeline() {
        VendorConnectorService service = mock(VendorConnectorService.class);
        VendorConnectorController controller = new VendorConnectorController(service);
        ConnectorPipelineStepDTO step = new ConnectorPipelineStepDTO(
                "transport", "TRANSPORT", "legacy-http", "1.0.0", 0,
                true, Map.of(), null);
        VendorConnectorSaveDraftRequestDTO request =
                new VendorConnectorSaveDraftRequestDTO(7, List.of(step));
        VendorConnectorDraftDTO expected = new VendorConnectorDraftDTO(
                11L, 42L, 8, 3, request.pipelineSnapshot());
        org.mockito.Mockito.when(service.saveDraft(42L, request, 99L)).thenReturn(expected);

        try (var context = mockStatic(UserContext.class)) {
            context.when(() -> UserContext.hasPermission("connector-plugin:bind")).thenReturn(true);
            context.when(UserContext::getCurrentUserId).thenReturn(99L);

            var result = controller.saveDraft(42L, request);

            assertEquals(200, result.getCode());
            assertSame(expected, result.getData());
            verify(service).saveDraft(42L, request, 99L);
        }
    }

    @Test
    void retiredRawWriteSurfaceUsesHttpGone() {
        VendorConnectorController controller = new VendorConnectorController(mock(VendorConnectorService.class));

        var response = controller.retired(new ConnectorLegacyWriteRetiredException(
                "CONNECTOR_LEGACY_WRITE_RETIRED"));

        assertEquals(410, response.getStatusCode().value());
        assertEquals(410, response.getBody().getCode());
    }

    private void assertGet(String name, Class<?>[] parameters, String path) throws Exception {
        Method method = VendorConnectorController.class.getMethod(name, parameters);
        assertArrayEquals(new String[]{path}, method.getAnnotation(GetMapping.class).value());
    }

    private void assertPost(String name, Class<?>[] parameters, String path) throws Exception {
        Method method = VendorConnectorController.class.getMethod(name, parameters);
        assertArrayEquals(new String[]{path}, method.getAnnotation(PostMapping.class).value());
    }

    private void assertPut(String name, Class<?>[] parameters, String path) throws Exception {
        Method method = VendorConnectorController.class.getMethod(name, parameters);
        assertArrayEquals(new String[]{path}, method.getAnnotation(PutMapping.class).value());
    }
}
