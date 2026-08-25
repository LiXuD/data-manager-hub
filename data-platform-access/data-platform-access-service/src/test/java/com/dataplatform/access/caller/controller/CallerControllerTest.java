package com.dataplatform.access.caller.controller;

import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.common.util.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallerControllerTest {

    private final CallerService callerService = mock(CallerService.class);
    private final CallerController controller = new CallerController();

    CallerControllerTest() {
        ReflectionTestUtils.setField(controller, "callerService", callerService);
    }

    @Test
    void createScopesCallerToCurrentTenant() {
        CallerInfo caller = new CallerInfo();
        caller.setCallerCode("SYSTEM_A");
        caller.setCallerName("系统 A");
        caller.setTenantId(99L);
        when(callerService.getByCode("SYSTEM_A")).thenReturn(null);
        when(callerService.save(any(CallerInfo.class))).thenReturn(true);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);

            var response = controller.create(caller);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().getData().getTenantId()).isEqualTo(7L);
            verify(callerService).save(caller);
        }
    }
}
