package com.dataplatform.access.caller.controller;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.access.caller.vo.ApiKeyRateLimitUpdateVO;
import com.dataplatform.access.caller.vo.ApiKeyResponse;
import com.dataplatform.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiKeyControllerRateLimitTest {

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
    void shouldUpdateRateLimitPolicy() {
        ApiKeyRateLimitUpdateVO request = request(false, 600);
        ApiKey updated = new ApiKey();
        updated.setId(7L);
        updated.setRateLimitEnabled(false);
        updated.setRateLimit(600);
        ApiKey existing = new ApiKey();
        existing.setId(7L);
        existing.setCallerId(1L);
        CallerInfo caller = new CallerInfo();
        caller.setId(1L);
        when(apiKeyService.getById(7L)).thenReturn(existing);
        when(callerService.getById(1L)).thenReturn(caller);
        when(apiKeyService.updateRateLimitPolicy(7L, false, 600)).thenReturn(updated);

        ResponseEntity<Result<ApiKeyResponse>> response;
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);
            response = controller.updateRateLimit(7L, request);
        }

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().getData().rateLimitEnabled());
        assertEquals(600, response.getBody().getData().rateLimit());
        verify(apiKeyService).updateRateLimitPolicy(7L, false, 600);
    }

    @Test
    void shouldRejectInvalidRateLimitPolicy() {
        ResponseEntity<Result<ApiKeyResponse>> response = controller.updateRateLimit(7L, request(true, 0));

        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void shouldReturnNotFoundForMissingApiKey() {
        ApiKeyRateLimitUpdateVO request = request(true, 100);
        when(apiKeyService.getById(99L)).thenReturn(null);

        ResponseEntity<Result<ApiKeyResponse>> response = controller.updateRateLimit(99L, request);

        assertEquals(404, response.getStatusCode().value());
        verify(apiKeyService, never()).updateRateLimitPolicy(99L, true, 100);
    }

    private ApiKeyRateLimitUpdateVO request(boolean enabled, int rateLimit) {
        ApiKeyRateLimitUpdateVO request = new ApiKeyRateLimitUpdateVO();
        request.setRateLimitEnabled(enabled);
        request.setRateLimit(rateLimit);
        return request;
    }
}
