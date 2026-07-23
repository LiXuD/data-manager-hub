package com.dataplatform.masterdata.interface_.controller;

import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceCreateReqDTO;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceUpdateReqDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.interface_.service.InterfaceContractService;
import com.dataplatform.common.util.UserContext;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mockStatic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ApiInterfaceControllerTest {

    private final ApiInterfaceService apiInterfaceService = mock(ApiInterfaceService.class);
    private final InterfaceContractService interfaceContractService = mock(InterfaceContractService.class);
    private final ApiInterfaceController controller = new ApiInterfaceController(
            apiInterfaceService, interfaceContractService);

    @Test
    void rejectsSchemaSnapshotDuringCreate() {
        ApiInterfaceCreateReqDTO request = new ApiInterfaceCreateReqDTO();
        request.setRequestSchema("{\"type\":\"object\"}");

        var result = controller.create(request);

        assertThat(result.getCode()).isEqualTo(400);
        verifyNoInteractions(apiInterfaceService, interfaceContractService);
    }

    @Test
    void rejectsSchemaSnapshotDuringOrdinaryUpdate() {
        ApiInterfaceUpdateReqDTO request = new ApiInterfaceUpdateReqDTO();
        request.setResponseSchema("{\"type\":\"object\"}");

        var result = controller.update(1L, request);

        assertThat(result.getCode()).isEqualTo(400);
        verifyNoInteractions(apiInterfaceService, interfaceContractService);
    }

    @Test
    void rejectsContractEndpointsWithoutPermission() {
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("interface:view")).thenReturn(false);
            userContext.when(() -> UserContext.hasPermission("interface:edit")).thenReturn(false);

            assertThat(controller.getContract(1L).getCode()).isEqualTo(403);
            assertThat(controller.updateContract(1L, new InterfaceContractDTO()).getCode()).isEqualTo(403);

            verifyNoInteractions(apiInterfaceService, interfaceContractService);
        }
    }

    @Test
    void mapsContractValidationExceptionToPhysicalBadRequest() {
        var response = controller.handleContractValidation(new IllegalArgumentException("字段名格式无效"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo(400);
    }
}
