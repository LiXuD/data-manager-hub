package com.dataplatform.masterdata.connector.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationActionRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationObserveRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationStartRequestDTO;
import com.dataplatform.masterdata.connector.service.VendorConnectorMigrationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
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
    void exposesControlledMigrationActionsWithMigrationPermission() {
        try (var context = mockStatic(UserContext.class)) {
            context.when(() -> UserContext.hasPermission("connector-plugin:view")).thenReturn(true);
            org.mockito.Mockito.when(service.list("STABLE")).thenReturn(List.of());
            assertEquals(200, controller.list("STABLE").getCode());
            verify(service).list("STABLE");

            context.when(() -> UserContext.hasPermission("connector-plugin:migrate")).thenReturn(false);
            assertEquals(403, controller.prepare(42L).getCode());
            assertEquals(403, controller.startObservation(42L,
                    new VendorConnectorMigrationStartRequestDTO()).getCode());
            assertEquals(403, controller.observe(42L,
                    new VendorConnectorMigrationObserveRequestDTO()).getCode());
            assertEquals(403, controller.complete(42L,
                    new VendorConnectorMigrationActionRequestDTO()).getCode());
            assertEquals(403, controller.rollback(42L,
                    new VendorConnectorMigrationActionRequestDTO()).getCode());
        }
        long getRoutes = java.util.Arrays.stream(VendorConnectorMigrationController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .count();
        long postRoutes = java.util.Arrays.stream(VendorConnectorMigrationController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .count();
        assertEquals(1, getRoutes);
        assertEquals(5, postRoutes);
    }
}
