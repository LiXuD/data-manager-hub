package com.dataplatform.masterdata.vendor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigCreateReqDTO;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.service.VendorConfigService;
import com.dataplatform.masterdata.vendor.service.VendorHealthService;
import org.junit.jupiter.api.Test;

class VendorConfigControllerTest {

    private final VendorConfigService service = mock(VendorConfigService.class);
    private final VendorConfigController controller = new VendorConfigController(
            service, mock(VendorHealthService.class));

    @Test
    void resolvesDataTypeCodeBeforePersistingVendorConfig() {
        VendorConfigCreateReqDTO request = request();
        when(service.getDataTypeIdByCode("WORLD_TIME")).thenReturn(42L);
        when(service.save(any(VendorConfig.class))).thenAnswer(invocation -> {
            VendorConfig config = invocation.getArgument(0);
            config.setId(9L);
            return true;
        });

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("vendor:edit")).thenReturn(true);

            var result = controller.create(request);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().getDataTypeId()).isEqualTo(42L);
            verify(service).save(any(VendorConfig.class));
        }
    }

    @Test
    void rejectsUnknownDataTypeBeforePersistence() {
        VendorConfigCreateReqDTO request = request();
        when(service.getDataTypeIdByCode("WORLD_TIME")).thenReturn(null);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("vendor:edit")).thenReturn(true);

            var result = controller.create(request);

            assertThat(result.getCode()).isEqualTo(400);
            verify(service).getDataTypeIdByCode("WORLD_TIME");
            verify(service, never()).save(any(VendorConfig.class));
        }
    }

    private VendorConfigCreateReqDTO request() {
        VendorConfigCreateReqDTO request = new VendorConfigCreateReqDTO();
        request.setVendorId(1L);
        request.setInterfaceId(2L);
        request.setDataTypeCode("WORLD_TIME");
        request.setApiUrl("https://uapis.cn/api/v1/misc/worldtime");
        request.setMethod("GET");
        return request;
    }
}
