package com.dataplatform.masterdata.connector.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.connector.api.dto.PluginImportRequestDTO;
import com.dataplatform.masterdata.connector.service.ConnectorPluginCatalogService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ConnectorPluginControllerAuthorizationTest {
    private final ConnectorPluginCatalogService service = mock(ConnectorPluginCatalogService.class);
    private final ConnectorPluginController controller = new ConnectorPluginController(service);

    @Test
    void deniesEveryOperationWithoutDedicatedPermissions() {
        try (MockedStatic<UserContext> context = mockStatic(UserContext.class)) {
            context.when(() -> UserContext.hasPermission(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(false);
            assertEquals(403, controller.list().getCode());
            assertEquals(403, controller.get("demo").getCode());
            assertEquals(403, controller.versions("demo").getCode());
            assertEquals(403, controller.importVersion(
                    new PluginImportRequestDTO("https://repo/x", "a", "b", "c")).getCode());
            assertEquals(403, controller.verify("demo", "1.0.0").getCode());
            assertEquals(403, controller.stage("demo", "1.0.0").getCode());
            assertEquals(403, controller.activation("demo", "1.0.0").getCode());
            assertEquals(403, controller.activate("demo", "1.0.0").getCode());
            assertEquals(403, controller.disable("demo", "1.0.0").getCode());
            verifyNoInteractions(service);
        }
    }
}
