package com.dataplatform.masterdata.connector.spec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecConvertRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecConversionPreviewDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecPublishRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecRollbackRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecSaveRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecTestRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecUpgradePreviewRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecVersionDTO;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class ConnectorSpecControllerContractTest {

    @Test
    void controllerAndTransactionalServiceAreProxyable() throws Exception {
        assertFalse(Modifier.isFinal(ConnectorSpecController.class.getModifiers()));
        assertFalse(Modifier.isFinal(ConnectorSpecServiceImpl.class.getModifiers()));
        Method publish = ConnectorSpecServiceImpl.class.getMethod("publish", Long.class,
                ConnectorSpecPublishRequestDTO.class, Long.class);
        Transactional transactional = publish.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertEquals(Isolation.REPEATABLE_READ, transactional.isolation());
        Method rollback = ConnectorSpecServiceImpl.class.getMethod("rollback", Long.class,
                Integer.class, ConnectorSpecRollbackRequestDTO.class, Long.class);
        assertEquals(Isolation.REPEATABLE_READ,
                rollback.getAnnotation(Transactional.class).isolation());
        Method preview = ConnectorSpecServiceImpl.class.getMethod("upgradePreview", Long.class,
                ConnectorSpecUpgradePreviewRequestDTO.class);
        assertEquals(Isolation.REPEATABLE_READ,
                preview.getAnnotation(Transactional.class).isolation());
        assertTrue(preview.getAnnotation(Transactional.class).readOnly());
        Method conversionPreview = ConnectorSpecServiceImpl.class.getMethod(
                "convertPreview", Long.class);
        assertEquals(Isolation.REPEATABLE_READ,
                conversionPreview.getAnnotation(Transactional.class).isolation());
        assertTrue(conversionPreview.getAnnotation(Transactional.class).readOnly());
        Method convert = ConnectorSpecServiceImpl.class.getMethod("convert", Long.class,
                ConnectorSpecConvertRequestDTO.class, Long.class);
        assertEquals(Isolation.REPEATABLE_READ,
                convert.getAnnotation(Transactional.class).isolation());
    }

    @Test
    void exposesExactRoutesPermissionsAndRedactedOperationLogs() throws Exception {
        assertArrayEquals(new String[]{"/vendor/config/{configId}/connector-spec"},
                ConnectorSpecController.class.getAnnotation(RequestMapping.class).value());
        assertRoute("catalog", new Class<?>[]{Long.class}, GetMapping.class, "/catalog");
        assertRoute("versions", new Class<?>[]{Long.class, String.class}, GetMapping.class,
                "/catalog/{pluginId}/versions");
        assertRoute("draft", new Class<?>[]{Long.class}, GetMapping.class, "/draft");
        assertRoute("saveDraft", new Class<?>[]{Long.class, ConnectorSpecSaveRequestDTO.class},
                PutMapping.class, "/draft");
        assertRoute("validate", new Class<?>[]{Long.class}, PostMapping.class, "/validate");
        assertRoute("executionPlan", new Class<?>[]{Long.class, Integer.class}, GetMapping.class,
                "/execution-plan");
        assertRoute("test", new Class<?>[]{Long.class, ConnectorSpecTestRequestDTO.class},
                PostMapping.class, "/test");
        assertRoute("publish", new Class<?>[]{Long.class, ConnectorSpecPublishRequestDTO.class},
                PostMapping.class, "/publish");
        assertRoute("history", new Class<?>[]{Long.class}, GetMapping.class, "/versions");
        assertRoute("rollback", new Class<?>[]{Long.class, Integer.class,
                        ConnectorSpecRollbackRequestDTO.class},
                PostMapping.class, "/rollback/{version}");
        assertRoute("upgradePreview", new Class<?>[]{Long.class,
                        ConnectorSpecUpgradePreviewRequestDTO.class},
                PostMapping.class, "/upgrade-preview");
        assertRoute("convertPreview", new Class<?>[]{Long.class},
                PostMapping.class, "/convert-preview");
        assertRoute("convert", new Class<?>[]{Long.class, ConnectorSpecConvertRequestDTO.class},
                PostMapping.class, "/convert");

        for (Method method : ConnectorSpecController.class.getDeclaredMethods()) {
            OperationLog log = method.getAnnotation(OperationLog.class);
            if (log != null) {
                assertFalse(log.saveParams());
                assertFalse(log.saveResult());
            }
        }

        ConnectorSpecService service = mock(ConnectorSpecService.class);
        ConnectorSpecController controller = new ConnectorSpecController(service);
        try (var user = mockStatic(UserContext.class)) {
            user.when(() -> UserContext.hasPermission("connector-plugin:view")).thenReturn(false);
            assertEquals(403, controller.catalog(1L).getCode());
            assertEquals(403, controller.draft(1L).getCode());
            assertEquals(403, controller.executionPlan(1L, null).getCode());
            user.when(() -> UserContext.hasPermission("connector-plugin:test")).thenReturn(false);
            assertEquals(403, controller.test(1L, new ConnectorSpecTestRequestDTO()).getCode());
            user.when(() -> UserContext.hasPermission("connector-plugin:publish")).thenReturn(false);
            assertEquals(403, controller.publish(1L, new ConnectorSpecPublishRequestDTO(1)).getCode());
            user.when(() -> UserContext.hasPermission("connector-plugin:rollback")).thenReturn(false);
            assertEquals(403, controller.rollback(
                    1L, 1, new ConnectorSpecRollbackRequestDTO(1)).getCode());
            user.when(() -> UserContext.hasPermission("connector-plugin:bind")).thenReturn(false);
            assertEquals(403, controller.upgradePreview(1L,
                    new ConnectorSpecUpgradePreviewRequestDTO(1, "2.0.0")).getCode());
            assertEquals(403, controller.convertPreview(1L).getCode());
            assertEquals(403, controller.convert(
                    1L, new ConnectorSpecConvertRequestDTO(1)).getCode());
        }
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.invalid(new IllegalArgumentException("bad")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                controller.notFound(new ConnectorSpecNotFoundException("missing")).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,
                controller.conflict(new com.dataplatform.masterdata.connector.service.ConnectorConflictException(
                        "conflict")).getStatusCode());
        ConnectorSpecConversionPreviewDTO rejected = new ConnectorSpecConversionPreviewDTO(
                false, "MUST_REMAIN_LEGACY", "LEGACY_PIPELINE_NOT_CONVERTIBLE",
                List.of(new ConnectorSpecConversionPreviewDTO.Reason(
                        "CONFIG_VALUE_UNSUPPORTED", 0, "request-builder", "配置不等价")), null);
        var rejectedResponse = controller.notConvertible(
                new LegacyPipelineNotConvertibleException(rejected));
        assertEquals(HttpStatus.CONFLICT, rejectedResponse.getStatusCode());
        assertEquals("LEGACY_PIPELINE_NOT_CONVERTIBLE", rejectedResponse.getBody().getMsg());
        assertEquals(rejected, rejectedResponse.getBody().getData());
    }

    @Test
    void strictSaveWrapperCapturesUnknownControls() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        ConnectorSpecSaveRequestDTO request = mapper.readValue("""
                {"expectedDraftVersion":0,"connectorSpec":{"specVersion":"1",
                 "plugin":{"pluginId":"abc","pluginVersion":"1.0.0"},"config":{}},
                 "forcePublish":true}
                """, ConnectorSpecSaveRequestDTO.class);
        assertEquals(java.util.Set.of("forcePublish"), request.unknownFieldNames());
        assertNull(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(
                new com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDraftViewDTO(
                        true, 1L, 2L, 1, "SIMPLE_CONNECTOR", 0,
                        new ConnectorSpecDTO("1", new ConnectorSpecDTO.PluginRef("abc", "1.0.0"),
                                Map.of(), null), "a", "1.0.0", "b", "c"))
                .get("pipelineSnapshot"));

        ConnectorSpecTestRequestDTO test = mapper.readValue(
                "{\"params\":{\"name\":\"value\"},\"rawResponse\":true}",
                ConnectorSpecTestRequestDTO.class);
        assertEquals(java.util.Set.of("rawResponse"), test.unknownFieldNames());
        ConnectorSpecPublishRequestDTO publish = mapper.readValue(
                "{\"expectedDraftVersion\":1,\"force\":true}",
                ConnectorSpecPublishRequestDTO.class);
        assertEquals(java.util.Set.of("force"), publish.unknownFieldNames());
        ConnectorSpecRollbackRequestDTO rollback = mapper.readValue(
                "{\"expectedConnectorVersion\":1,\"force\":true}",
                ConnectorSpecRollbackRequestDTO.class);
        assertEquals(java.util.Set.of("force"), rollback.unknownFieldNames());
        ConnectorSpecUpgradePreviewRequestDTO preview = mapper.readValue(
                "{\"expectedDraftVersion\":1,\"targetPluginVersion\":\"2.0.0\",\"pluginId\":\"other\"}",
                ConnectorSpecUpgradePreviewRequestDTO.class);
        assertEquals(java.util.Set.of("pluginId"), preview.unknownFieldNames());
        ConnectorSpecConvertRequestDTO convert = mapper.readValue(
                "{\"expectedDraftVersion\":1,\"force\":true}",
                ConnectorSpecConvertRequestDTO.class);
        assertEquals(java.util.Set.of("force"), convert.unknownFieldNames());

        ConnectorSpecVersionDTO version = new ConnectorSpecVersionDTO(
                1L, 2L, 3, "SIMPLE_CONNECTOR", "a", "1.0.0", "b", "c",
                "V2_EMBEDDED", "c", 0, "ACTIVE", null, null, 9L);
        assertNull(mapper.valueToTree(version).get("pipelineSnapshot"));
        var history = new com.dataplatform.masterdata.connector.api.dto.ConnectorSpecHistoryDTO(
                List.of(new com.dataplatform.masterdata.connector.api.dto.ConnectorSpecHistoryDTO.Version(
                        1L, 2L, 3, "ADVANCED_LEGACY", null, null, null, null,
                        "a", "V1_DERIVED", "b", 0, "SUPERSEDED", null, null, 9L)));
        var historyJson = mapper.valueToTree(history);
        assertNull(historyJson.get("pipelineSnapshot"));
        assertFalse(historyJson.toString().contains("stageConfig"));
        var previewResponse = new com.dataplatform.masterdata.connector.api.dto.ConnectorSpecUpgradePreviewDTO(
                new com.dataplatform.masterdata.connector.api.dto.ConnectorSpecUpgradePreviewDTO.PluginCoordinate(
                        "demo", "1.0.0"),
                new com.dataplatform.masterdata.connector.api.dto.ConnectorSpecUpgradePreviewDTO.PluginCoordinate(
                        "demo", "2.0.0"), true, null, null, List.of(), List.of(),
                new com.dataplatform.masterdata.connector.api.dto.ConnectorSpecUpgradePreviewDTO.PlanDiff(
                        0, 0, 2, 0, 2, List.of("connector.request-builder")),
                "a", "b", "c");
        String previewJson = mapper.writeValueAsString(previewResponse);
        assertFalse(previewJson.contains("pipelineSnapshot"));
        assertFalse(previewJson.contains("stageConfig"));
        assertFalse(previewJson.contains("secretRef"));
    }

    private void assertRoute(String name, Class<?>[] parameters,
                             Class<? extends java.lang.annotation.Annotation> annotation,
                             String expected) throws Exception {
        Method method = ConnectorSpecController.class.getMethod(name, parameters);
        String[] paths;
        if (annotation == GetMapping.class) paths = method.getAnnotation(GetMapping.class).value();
        else if (annotation == PostMapping.class) paths = method.getAnnotation(PostMapping.class).value();
        else paths = method.getAnnotation(PutMapping.class).value();
        assertArrayEquals(new String[]{expected}, paths);
    }
}
