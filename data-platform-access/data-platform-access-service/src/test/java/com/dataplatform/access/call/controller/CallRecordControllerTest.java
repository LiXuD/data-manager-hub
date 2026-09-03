package com.dataplatform.access.call.controller;

import com.dataplatform.access.call.service.CallRecordService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.util.UserContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CallRecordControllerTest {

    private CallRecordService callRecordService;
    private CallRecordController controller;

    @BeforeEach
    void setUp() {
        callRecordService = mock(CallRecordService.class);
        controller = new CallRecordController();
        ReflectionTestUtils.setField(controller, "callRecordService", callRecordService);
    }

    @Test
    void rejectsMissingQueryBodyAsBadRequest() {
        assertEquals(400, controller.query(null).getStatusCode().value());
        verifyNoInteractions(callRecordService);
    }

    @Test
    void rejectsMalformedQueryFieldsAsBadRequestInsteadOfServerError() {
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(20L);
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(false);

            assertEquals(400, controller.query(Map.of("page", "not-a-number"))
                    .getStatusCode().value());
            assertEquals(400, controller.query(Map.of("callerId", true))
                    .getStatusCode().value());
        }
        verifyNoInteractions(callRecordService);
    }

    @Test
    void parsesValidQueryAndAlwaysPassesTenantScopeToService() {
        PageResult<com.dataplatform.common.entity.CallRecord> page = new PageResult<>();
        when(callRecordService.list(eq(20L), eq(1L), eq(2L), eq("company"), eq(Boolean.TRUE),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(2), eq(25)))
                .thenReturn(page);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(20L);
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(false);

            assertEquals(200, controller.query(Map.of(
                    "page", 2,
                    "pageSize", 25,
                    "callerId", 1,
                    "vendorId", 2,
                    "dataType", " company ",
                    "success", true)).getStatusCode().value());
        }
        verify(callRecordService).list(eq(20L), eq(1L), eq(2L), eq("company"), eq(Boolean.TRUE),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(2), eq(25));
    }
}
