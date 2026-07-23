package com.dataplatform.access.caller.controller;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.common.result.Result;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiKeyControllerCreateTest {

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
    void rejectsMissingBlankAndNonStringNames() {
        assertEquals(400, controller.create(Map.of("callerId", 1L)).getStatusCode().value());
        assertEquals(400, controller.create(Map.of("callerId", 1L, "name", "   ")).getStatusCode().value());
        assertEquals(400, controller.create(Map.of("callerId", 1L, "name", 42)).getStatusCode().value());

        verifyNoInteractions(apiKeyService, callerService);
    }

    @Test
    void rejectsNullBodyAndInvalidCallerId() {
        assertEquals(400, controller.create(null).getStatusCode().value());
        Map<String, Object> request = new HashMap<>();
        request.put("callerId", null);
        request.put("name", "key");
        assertEquals(400, controller.create(request).getStatusCode().value());

        verifyNoInteractions(apiKeyService, callerService);
    }

    @Test
    void trimsNameBeforeCreatingKey() {
        CallerInfo caller = new CallerInfo();
        caller.setId(1L);
        ApiKey created = new ApiKey();
        created.setId(7L);
        when(callerService.getById(1L)).thenReturn(caller);
        when(apiKeyService.createApiKey(1L, "key")).thenReturn(created);

        ResponseEntity<Result<ApiKey>> response =
                controller.create(Map.of("callerId", 1L, "name", "  key  "));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(7L, response.getBody().getData().getId());
        verify(apiKeyService).createApiKey(1L, "key");
    }
}
