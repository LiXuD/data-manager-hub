package com.dataplatform.masterdata.interface_.controller;

import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceCreateReqDTO;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceUpdateReqDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.interface_.service.InterfaceContractService;
import com.dataplatform.masterdata.interface_.service.InterfaceParamService;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.common.util.UserContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mockStatic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiInterfaceControllerTest {

    private final ApiInterfaceService apiInterfaceService = mock(ApiInterfaceService.class);
    private final InterfaceParamService interfaceParamService = mock(InterfaceParamService.class);
    private final InterfaceContractService interfaceContractService = mock(InterfaceContractService.class);
    private final ApiInterfaceController controller = new ApiInterfaceController(
            apiInterfaceService, interfaceParamService, interfaceContractService);

    @Test
    void rejectsSchemaSnapshotDuringCreate() {
        ApiInterfaceCreateReqDTO request = new ApiInterfaceCreateReqDTO();
        request.setRequestSchema("{\"type\":\"object\"}");

        var result = controller.create(request);

        assertThat(result.getCode()).isEqualTo(400);
        verifyNoInteractions(apiInterfaceService, interfaceParamService, interfaceContractService);
    }

    @Test
    void rejectsSchemaSnapshotDuringOrdinaryUpdate() {
        ApiInterfaceUpdateReqDTO request = new ApiInterfaceUpdateReqDTO();
        request.setResponseSchema("{\"type\":\"object\"}");

        var result = controller.update(1L, request);

        assertThat(result.getCode()).isEqualTo(400);
        verifyNoInteractions(apiInterfaceService, interfaceParamService, interfaceContractService);
    }

    @Test
    void rejectsContractCompatibilityEndpointsWithoutPermission() {
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("interface:view")).thenReturn(false);
            userContext.when(() -> UserContext.hasPermission("interface:edit")).thenReturn(false);

            assertThat(controller.getSchema(1L).getCode()).isEqualTo(403);
            assertThat(controller.getContract(1L).getCode()).isEqualTo(403);
            assertThat(controller.listParams(1L).getCode()).isEqualTo(403);
            assertThat(controller.updateSchema(1L, Map.of()).getCode()).isEqualTo(403);
            assertThat(controller.updateContract(1L, new InterfaceContractDTO()).getCode()).isEqualTo(403);
            assertThat(controller.importSchema(1L).getCode()).isEqualTo(403);
            assertThat(controller.validateSchema(Map.of("schema", "{}")).getCode()).isEqualTo(403);
            assertThat(controller.addParam(1L, new InterfaceParamDTO()).getCode()).isEqualTo(403);
            assertThat(controller.batchSaveParams(1L, List.of()).getCode()).isEqualTo(403);
            assertThat(controller.updateParam(1L, new InterfaceParamDTO()).getCode()).isEqualTo(403);
            assertThat(controller.deleteParam(1L).getCode()).isEqualTo(403);

            verifyNoInteractions(apiInterfaceService, interfaceParamService, interfaceContractService);
        }
    }

    @Test
    void compatibilityAddDelegatesToTransactionalContractService() {
        ApiInterface apiInterface = new ApiInterface();
        apiInterface.setId(1L);
        InterfaceContractDTO contract = new InterfaceContractDTO();
        InterfaceParamDTO savedField = new InterfaceParamDTO();
        savedField.setId(10L);
        savedField.setParamName("city");
        InterfaceContractDTO saved = new InterfaceContractDTO();
        saved.setRequestFields(new java.util.ArrayList<>(List.of(savedField)));
        when(apiInterfaceService.getById(1L)).thenReturn(apiInterface);
        when(interfaceContractService.getContract(1L)).thenReturn(contract);
        when(interfaceContractService.saveContract(any(), any())).thenReturn(saved);
        InterfaceParamDTO input = new InterfaceParamDTO();
        input.setParamName("city");
        input.setParamType("string");

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("interface:edit")).thenReturn(true);

            var result = controller.addParam(1L, input);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().getId()).isEqualTo(10L);
            verify(interfaceContractService).saveContract(any(), any());
            verify(interfaceParamService, never()).save(any());
        }
    }

    @Test
    void mapsContractValidationExceptionToPhysicalBadRequest() {
        var response = controller.handleContractValidation(new IllegalArgumentException("字段名格式无效"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo(400);
    }
}
