package com.dataplatform.masterdata.connector.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.connector.api.dto.PluginImportRequestDTO;
import com.dataplatform.masterdata.connector.service.ConnectorPluginCatalogService;
import com.dataplatform.masterdata.connector.service.PluginArtifactValidationException;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConnectorPluginControllerValidationTest {
    private final ConnectorPluginCatalogService service = mock(ConnectorPluginCatalogService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ConnectorPluginController(service))
            .build();

    @Test
    void mapsExpectedHashValidationToSafeBadRequest() throws Exception {
        assertValidationFailure("插件制品SHA-256与期望值不一致");
    }

    @Test
    void mapsExpectedSignatureValidationToSafeBadRequest() throws Exception {
        assertValidationFailure("插件Ed25519签名验证失败");
    }

    @Test
    void doesNotMisclassifyUnexpectedFailuresAsArtifactValidation() {
        when(service.importVersion(any(PluginImportRequestDTO.class), eq(9L)))
                .thenThrow(new IllegalStateException("unexpected-internal-detail"));

        try (MockedStatic<UserContext> context = permittedContext()) {
            assertThrows(ServletException.class, () -> mockMvc.perform(post("/connector-plugin/versions/import")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequest())).andReturn());
        }
    }

    private void assertValidationFailure(String message) throws Exception {
        when(service.importVersion(any(PluginImportRequestDTO.class), eq(9L)))
                .thenThrow(new PluginArtifactValidationException(message, null));

        try (MockedStatic<UserContext> context = permittedContext()) {
            mockMvc.perform(post("/connector-plugin/versions/import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequest()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.msg").value(message))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    private MockedStatic<UserContext> permittedContext() {
        MockedStatic<UserContext> context = mockStatic(UserContext.class);
        context.when(() -> UserContext.hasPermission("connector-plugin:import")).thenReturn(true);
        context.when(UserContext::getCurrentUserId).thenReturn(9L);
        return context;
    }

    private String validRequest() {
        return """
                {"artifactUri":"https://repo.example/plugins/demo.jar",
                 "expectedSha256":"%s","detachedSignature":"signature","signingKeyId":"release"}
                """.formatted("a".repeat(64));
    }
}
