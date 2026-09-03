package com.dataplatform.masterdata.vendor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigCreateReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.service.VendorConfigConflictException;
import com.dataplatform.masterdata.vendor.service.VendorConfigDTOAssembler;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.service.VendorConfigService;
import com.dataplatform.masterdata.vendor.service.VendorHealthService;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VendorConfigControllerTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                VendorConfig.class);
    }

    private final VendorConfigService service = mock(VendorConfigService.class);
    private final ApiInterfaceService interfaceService = mock(ApiInterfaceService.class);
    private final VendorConfigDTOAssembler dtoAssembler = mock(VendorConfigDTOAssembler.class);
    private final VendorConfigController controller = new VendorConfigController(
            service, mock(VendorHealthService.class), interfaceService, dtoAssembler);

    @Test
    void resolvesDataTypeCodeBeforePersistingVendorConfig() {
        VendorConfigCreateReqDTO request = request();
        ApiInterface apiInterface = new ApiInterface();
        apiInterface.setId(2L);
        apiInterface.setDataTypeId(42L);
        when(interfaceService.getById(2L)).thenReturn(apiInterface);
        when(service.getDataTypeIdByCode("WORLD_TIME")).thenReturn(42L);
        when(service.createBinding(any(VendorConfig.class))).thenAnswer(invocation -> {
            VendorConfig config = invocation.getArgument(0);
            config.setId(9L);
            return config;
        });
        when(dtoAssembler.toDTO(any(VendorConfig.class))).thenAnswer(invocation -> {
            VendorConfig config = invocation.getArgument(0);
            VendorConfigDTO dto = new VendorConfigDTO();
            dto.setDataTypeId(config.getDataTypeId());
            dto.setRuntimeMode(config.getRuntimeMode());
            dto.setStatus(config.getStatus().getCode());
            dto.setActiveConnectorVersionId(config.getActiveConnectorVersionId());
            return dto;
        });

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("vendor:add")).thenReturn(true);

            var result = controller.create(request);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().getDataTypeId()).isEqualTo(42L);
            assertThat(result.getData().getRuntimeMode()).isEqualTo("PLUGIN");
            assertThat(result.getData().getStatus()).isEqualTo("inactive");
            assertThat(result.getData().getActiveConnectorVersionId()).isNull();
            verify(service).createBinding(any(VendorConfig.class));
        }
    }

    @Test
    void rejectsUnknownDataTypeBeforePersistence() {
        VendorConfigCreateReqDTO request = request();
        ApiInterface apiInterface = new ApiInterface();
        apiInterface.setId(2L);
        apiInterface.setDataTypeId(42L);
        when(interfaceService.getById(2L)).thenReturn(apiInterface);
        when(service.getDataTypeIdByCode("WORLD_TIME")).thenReturn(null);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("vendor:add")).thenReturn(true);

            var result = controller.create(request);

            assertThat(result.getCode()).isEqualTo(400);
            verify(service).getDataTypeIdByCode("WORLD_TIME");
            verify(service, never()).createBinding(any(VendorConfig.class));
        }
    }

    @Test
    void duplicateBindingIsHandledAsHttpConflict() {
        ApiInterface apiInterface = new ApiInterface();
        apiInterface.setId(2L);
        apiInterface.setDataTypeId(42L);
        when(interfaceService.getById(2L)).thenReturn(apiInterface);
        when(service.getDataTypeIdByCode("WORLD_TIME")).thenReturn(42L);
        when(service.createBinding(any(VendorConfig.class)))
                .thenThrow(new VendorConfigConflictException("当前接口已绑定该厂商"));

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("vendor:add")).thenReturn(true);
            assertThatThrownBy(() -> controller.create(request()))
                    .isInstanceOf(VendorConfigConflictException.class);
            var response = controller.conflict(new VendorConfigConflictException("当前接口已绑定该厂商"));
            assertThat(response.getStatusCode().value()).isEqualTo(409);
            assertThat(response.getBody().getCode()).isEqualTo(409);
        }
    }

    @Test
    void updateStatusWritesPersistedEnumCode() {
        when(service.canActivate(9L)).thenReturn(true);
        when(service.update(any(LambdaUpdateWrapper.class))).thenAnswer(invocation -> {
            LambdaUpdateWrapper<VendorConfig> wrapper = invocation.getArgument(0);
            assertThat(wrapper.getSqlSet()).contains("status");
            assertThat(wrapper.getParamNameValuePairs()).containsValue("active");
            return true;
        });

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("vendor:edit")).thenReturn(true);

            var result = controller.updateStatus(9L, Map.of("status", "active"));

            assertThat(result.getCode()).isEqualTo(200);
        }
    }

    @Test
    void returnsNotFoundForMissingConfiguration() {
        when(service.getById(404L)).thenReturn(null);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("vendor:view")).thenReturn(true);

            var result = controller.getById(404L);

            assertThat(result.getCode()).isEqualTo(404);
            verify(dtoAssembler, never()).toDTO(any(VendorConfig.class));
        }
    }

    @Test
    void rejectsInvalidCreatePolicyBeforePersistence() {
        VendorConfigCreateReqDTO request = request();
        request.setTimeout(0);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("vendor:add")).thenReturn(true);

            var result = controller.create(request);

            assertThat(result.getCode()).isEqualTo(400);
            verify(service, never()).createBinding(any(VendorConfig.class));
        }
    }

    @Test
    void rejectsActivationBeforeConnectorPublication() {
        when(service.canActivate(9L)).thenReturn(false);
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("vendor:edit")).thenReturn(true);

            var result = controller.updateStatus(9L, Map.of("status", "active"));

            assertThat(result.getCode()).isEqualTo(409);
            verify(service, never()).update(any(LambdaUpdateWrapper.class));
        }
    }

    private VendorConfigCreateReqDTO request() {
        VendorConfigCreateReqDTO request = new VendorConfigCreateReqDTO();
        request.setVendorId(1L);
        request.setInterfaceId(2L);
        request.setDataTypeCode("WORLD_TIME");
        return request;
    }
}
