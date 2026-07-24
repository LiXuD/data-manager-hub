package com.dataplatform.access.approval.controller;

import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.access.approval.api.CallerOptionResponse;
import com.dataplatform.access.approval.service.ApiPermissionGrantService;
import com.dataplatform.common.util.UserContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiPermissionGrantControllerTest {

    private final ApiPermissionGrantService grantService = mock(ApiPermissionGrantService.class);
    private final ApiPermissionGrantController controller =
            new ApiPermissionGrantController(grantService);

    @Test
    void emergencyOptionsRequireEmergencyGrantPermission() {
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("api-permission:emergency-grant"))
                    .thenReturn(false);

            assertThatThrownBy(controller::emergencyCallers)
                    .isInstanceOfSatisfying(ApiPermissionException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo("PERMISSION_DENIED"));
            verifyNoInteractions(grantService);
        }
    }

    @Test
    void emergencyOptionsUseCurrentTenant() {
        CallerOptionResponse caller = new CallerOptionResponse(1L, "SYSTEM_A", "系统 A");
        when(grantService.emergencyCallers(7L)).thenReturn(List.of(caller));
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("api-permission:emergency-grant"))
                    .thenReturn(true);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);

            var result = controller.emergencyCallers();

            assertThat(result.getData()).containsExactly(caller);
        }
    }
}
