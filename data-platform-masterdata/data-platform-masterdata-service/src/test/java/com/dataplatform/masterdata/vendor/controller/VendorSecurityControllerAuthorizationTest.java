package com.dataplatform.masterdata.vendor.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityOrderReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityPreviewReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecuritySaveReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityStepListDTO;
import com.dataplatform.masterdata.vendor.service.VendorHealthService;
import com.dataplatform.masterdata.vendor.service.VendorSecurityService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class VendorSecurityControllerAuthorizationTest {

    private final VendorSecurityService securityService = mock(VendorSecurityService.class);
    private final VendorHealthService healthService = mock(VendorHealthService.class);
    private final VendorSecurityController controller = new VendorSecurityController(securityService, healthService);

    @Test
    void rejectsEverySecurityOperationWithoutRequiredPermission() {
        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("vendor:view")).thenReturn(false);
            userContext.when(() -> UserContext.hasPermission("vendor:edit")).thenReturn(false);

            assertEquals(403, controller.capabilities().getCode());
            assertEquals(403, controller.getSteps(1L).getCode());
            assertEquals(403, controller.saveSteps(1L, new VendorSecuritySaveReqDTO()).getCode());
            assertEquals(403, controller.reorder(1L, new VendorSecurityOrderReqDTO()).getCode());
            assertEquals(403, controller.preview(1L, new VendorSecurityPreviewReqDTO()).getCode());
            assertEquals(403, controller.test(1L).getCode());
            assertEquals(403, controller.history(1L).getCode());
            assertEquals(403, controller.rollback(1L, 2L, 0).getCode());

            verifyNoInteractions(securityService, healthService);
        }
    }

    @Test
    void viewPermissionAllowsOnlyReadOperations() {
        when(securityService.capabilities()).thenReturn(List.of());
        when(securityService.getSteps(1L)).thenReturn(new VendorSecurityStepListDTO());
        when(securityService.history(1L)).thenReturn(List.of());
        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("vendor:view")).thenReturn(true);
            userContext.when(() -> UserContext.hasPermission("vendor:edit")).thenReturn(false);

            assertEquals(200, controller.capabilities().getCode());
            assertEquals(200, controller.getSteps(1L).getCode());
            assertEquals(200, controller.history(1L).getCode());
            assertEquals(403, controller.preview(1L, new VendorSecurityPreviewReqDTO()).getCode());
        }
    }
}
