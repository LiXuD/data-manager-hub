package com.dataplatform.masterdata.vendor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.vendor.entity.VendorExtendedConfig;
import com.dataplatform.masterdata.vendor.service.VendorExtendedConfigService;
import com.dataplatform.masterdata.vendor.service.VendorConfigService;
import com.dataplatform.masterdata.vendor.service.VendorHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VendorExtendedConfigControllerAuthorizationTest {

    private final VendorExtendedConfigService service = mock(VendorExtendedConfigService.class);

    @Test
    void configControllerRejectsReadAndWriteWithoutVendorPermissions() {
        ConfigController controller = new ConfigController();
        ReflectionTestUtils.setField(controller, "configService", service);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("vendor:view")).thenReturn(false);
            userContext.when(() -> UserContext.hasPermission("vendor:edit")).thenReturn(false);

            assertThat(controller.list(null, null, 1, 10).getCode()).isEqualTo(403);
            assertThat(controller.get(1L).getStatusCode().value()).isEqualTo(403);
            assertThat(controller.create(new VendorExtendedConfig()).getStatusCode().value()).isEqualTo(403);
            assertThat(controller.clearCache().getCode()).isEqualTo(403);
            verifyNoInteractions(service);
        }
    }

    @Test
    void vendorExtendedControllerRejectsReadAndWriteWithoutVendorPermissions() {
        VendorExtendedConfigController controller = new VendorExtendedConfigController();
        ReflectionTestUtils.setField(controller, "extendedConfigService", service);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("vendor:view")).thenReturn(false);
            userContext.when(() -> UserContext.hasPermission("vendor:edit")).thenReturn(false);

            assertThat(controller.list(null, null, 1, 10).getCode()).isEqualTo(403);
            assertThat(controller.get(1L).getStatusCode().value()).isEqualTo(403);
            assertThat(controller.create(new VendorExtendedConfig()).getStatusCode().value()).isEqualTo(403);
            verifyNoInteractions(service);
        }
    }

    @Test
    void vendorConfigControllerRejectsReadWithoutVendorPermission() {
        VendorConfigService vendorConfigService = mock(VendorConfigService.class);
        VendorHealthService healthService = mock(VendorHealthService.class);
        VendorConfigController controller = new VendorConfigController(vendorConfigService, healthService);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("vendor:view")).thenReturn(false);

            assertThat(controller.list(null, null, null, null).getCode()).isEqualTo(403);
            assertThat(controller.getById(1L).getCode()).isEqualTo(403);
            verifyNoInteractions(vendorConfigService, healthService);
        }
    }
}
