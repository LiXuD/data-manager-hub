package com.dataplatform.masterdata.interface_.controller;

import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiInterfaceInternalControllerTest {

    @Test
    void mapsEnumStatusToCrossDomainStringCode() {
        ApiInterfaceService service = mock(ApiInterfaceService.class);
        ApiInterface entity = new ApiInterface();
        entity.setId(1L);
        entity.setInterfaceCode("COMPANY_BASE");
        entity.setStatus(CommonStatus.ACTIVE);
        when(service.getById(1L)).thenReturn(entity);

        ApiInterfaceInternalController controller = new ApiInterfaceInternalController();
        ReflectionTestUtils.setField(controller, "apiInterfaceService", service);

        var result = controller.getById(1L);

        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getStatus()).isEqualTo("active");
    }
}
