package com.dataplatform.access.caller.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.util.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApiKeyControllerDeleteTest {

    private ApiKeyService apiKeyService;
    private CallerService callerService;
    private ApiKeyController controller;

    @BeforeEach
    void setUp() {
        apiKeyService = mock(ApiKeyService.class);
        callerService = mock(CallerService.class);
        controller = new ApiKeyController();
        ReflectionTestUtils.setField(controller, "apiKeyService", apiKeyService);
        ReflectionTestUtils.setField(controller, "callerService", callerService);
    }

    @Test
    void reportsDeletePersistenceConflict() {
        ApiKey key = key(7L, 1L);
        when(apiKeyService.getById(7L)).thenReturn(key);
        when(callerService.getById(1L)).thenReturn(caller(20L));
        when(apiKeyService.removeById(7L)).thenReturn(false);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(20L);
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(false);

            assertEquals(409, controller.delete(7L).getStatusCode().value());
        }
    }

    private ApiKey key(Long id, Long callerId) {
        ApiKey key = new ApiKey();
        key.setId(id);
        key.setCallerId(callerId);
        key.setStatus(ApiKeyStatus.ACTIVE);
        return key;
    }

    private CallerInfo caller(Long tenantId) {
        CallerInfo caller = new CallerInfo();
        caller.setTenantId(tenantId);
        return caller;
    }
}
