package com.dataplatform.access.caller.controller;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.util.UserContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiKeyControllerStatusTest {

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
    void rejectsNonStringStatusWithoutTouchingTheStore() {
        assertEquals(400, controller.updateStatus(7L, Map.of("status", 1)).getCode());
        verifyNoInteractions(apiKeyService, callerService);
    }

    @Test
    void updatesOnlyStatusAndDoesNotRewriteSecretFields() {
        ApiKey existing = key(7L, 1L);
        ApiKey latest = key(7L, 1L);
        latest.setStatus(ApiKeyStatus.REVOKED);
        when(apiKeyService.getById(7L)).thenReturn(existing, latest);
        CallerInfo caller = new CallerInfo();
        caller.setTenantId(20L);
        when(callerService.getById(1L)).thenReturn(caller);
        when(apiKeyService.updateById(any(ApiKey.class))).thenReturn(true);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(20L);
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(false);

            assertEquals(200, controller.updateStatus(7L, Map.of("status", "revoked")).getCode());
        }

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyService).updateById(captor.capture());
        ApiKey patch = captor.getValue();
        assertEquals(7L, patch.getId());
        assertEquals(ApiKeyStatus.REVOKED, patch.getStatus());
        assertNull(patch.getApiKey());
        assertNull(patch.getApiSecret());
    }

    @Test
    void reportsConcurrentStatusUpdateAsConflict() {
        ApiKey existing = key(7L, 1L);
        when(apiKeyService.getById(7L)).thenReturn(existing);
        CallerInfo caller = new CallerInfo();
        caller.setTenantId(20L);
        when(callerService.getById(1L)).thenReturn(caller);
        when(apiKeyService.updateById(any(ApiKey.class))).thenReturn(false);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(20L);
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(false);

            assertEquals(409, controller.updateStatus(7L, Map.of("status", "revoked")).getCode());
        }
    }

    private ApiKey key(Long id, Long callerId) {
        ApiKey key = new ApiKey();
        key.setId(id);
        key.setCallerId(callerId);
        key.setApiKey("secret-value");
        key.setApiSecret("secret-value-2");
        key.setStatus(ApiKeyStatus.ACTIVE);
        return key;
    }
}
