package com.dataplatform.masterdata.connector.spec.inventory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.util.UserContext;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

class ConnectorLegacyInventoryControllerContractTest {

    @Test
    void exposesPagedReadOnlyRouteWithViewPermissionAndRedactedAudit() throws Exception {
        assertFalse(Modifier.isFinal(ConnectorLegacyInventoryController.class.getModifiers()));
        assertFalse(Modifier.isFinal(ConnectorLegacyInventoryService.class.getModifiers()));
        assertArrayEquals(new String[]{"/vendor/config/connector-spec"},
                ConnectorLegacyInventoryController.class.getAnnotation(RequestMapping.class).value());
        Method method = ConnectorLegacyInventoryController.class.getMethod(
                "inventory", Integer.class, Integer.class);
        assertArrayEquals(new String[]{"/inventory"}, method.getAnnotation(GetMapping.class).value());
        OperationLog operationLog = method.getAnnotation(OperationLog.class);
        assertNotNull(operationLog);
        assertFalse(operationLog.saveParams());
        assertFalse(operationLog.saveResult());
        RequestParam page = method.getParameters()[0].getAnnotation(RequestParam.class);
        RequestParam pageSize = method.getParameters()[1].getAnnotation(RequestParam.class);
        assertEquals("1", page.defaultValue());
        assertEquals("50", pageSize.defaultValue());

        Method inventory = ConnectorLegacyInventoryService.class.getMethod(
                "inventory", Integer.class, Integer.class);
        Transactional transaction = inventory.getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertTrue(transaction.readOnly());
        assertEquals(Isolation.REPEATABLE_READ, transaction.isolation());

        ConnectorLegacyInventoryService service = mock(ConnectorLegacyInventoryService.class);
        ConnectorLegacyInventoryController controller =
                new ConnectorLegacyInventoryController(service);
        try (var user = mockStatic(UserContext.class)) {
            user.when(() -> UserContext.hasPermission("connector-plugin:view")).thenReturn(false);
            assertEquals(403, controller.inventory(1, 50).getCode());
        }
        verifyNoInteractions(service);
    }
}
