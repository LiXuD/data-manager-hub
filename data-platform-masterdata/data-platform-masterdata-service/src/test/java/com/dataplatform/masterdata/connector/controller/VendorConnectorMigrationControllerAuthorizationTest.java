package com.dataplatform.masterdata.connector.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.connector.service.VendorConnectorMigrationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

class VendorConnectorMigrationControllerAuthorizationTest {
    private final VendorConnectorMigrationService service = mock(VendorConnectorMigrationService.class);
    private final VendorConnectorMigrationController controller = new VendorConnectorMigrationController(service);

    @Test
    void deniesHistoryWithoutViewPermission() {
        try (var context = mockStatic(UserContext.class)) {
            context.when(() -> UserContext.hasPermission("connector-plugin:view")).thenReturn(false);
            assertEquals(403, controller.list(null).getCode());
            verifyNoInteractions(service);
        }
    }

    @Test
    void exposesOnlyReadOnlyMigrationHistory() {
        try (var context = mockStatic(UserContext.class)) {
            context.when(() -> UserContext.hasPermission("connector-plugin:view")).thenReturn(true);
            org.mockito.Mockito.when(service.list("STABLE")).thenReturn(List.of());
            assertEquals(200, controller.list("STABLE").getCode());
            verify(service).list("STABLE");
        }
        long postRoutes = java.util.Arrays.stream(VendorConnectorMigrationController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .count();
        assertEquals(0, postRoutes);
    }
}
