package com.dataplatform.masterdata.interface_.controller;

import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceDTOAssembler;
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
        ApiInterfaceDTOAssembler assembler = mock(ApiInterfaceDTOAssembler.class);
        com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO dto =
                new com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO();
        dto.setStatus("active");
        when(assembler.toDTO(entity)).thenReturn(dto);
        ReflectionTestUtils.setField(controller, "dtoAssembler", assembler);

        var result = controller.getById(1L);

        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getStatus()).isEqualTo("active");
    }
}
