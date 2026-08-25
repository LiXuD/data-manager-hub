package com.dataplatform.masterdata.vendor.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.connector.service.ActiveVendorConnectorRuntimeService;
import com.dataplatform.masterdata.vendor.entity.DataType;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.mapper.DataTypeMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorInfoMapper;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataQueryControllerTest {

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, VendorInfo.class);
        TableInfoHelper.initTableInfo(assistant, DataType.class);
        TableInfoHelper.initTableInfo(assistant, VendorConfig.class);
    }

    @Mock
    private VendorInfoMapper vendorInfoMapper;
    @Mock
    private DataTypeMapper dataTypeMapper;
    @Mock
    private VendorConfigMapper vendorConfigMapper;
    @Mock
    private ApiInterfaceService apiInterfaceService;
    @Mock
    private ActiveVendorConnectorRuntimeService connectorRuntimeService;

    @Test
    void shouldRejectUnknownInterfaceInsteadOfDroppingInterfaceFilter() {
        VendorInfo vendor = new VendorInfo();
        vendor.setId(1L);
        DataType dataType = new DataType();
        dataType.setId(2L);
        when(vendorInfoMapper.selectOne(any())).thenReturn(vendor);
        when(dataTypeMapper.selectOne(any())).thenReturn(dataType);
        when(apiInterfaceService.getByInterfaceCode("missing-interface")).thenReturn(null);

        DataQueryController controller = new DataQueryController();
        ReflectionTestUtils.setField(controller, "vendorInfoMapper", vendorInfoMapper);
        ReflectionTestUtils.setField(controller, "dataTypeMapper", dataTypeMapper);
        ReflectionTestUtils.setField(controller, "vendorConfigMapper", vendorConfigMapper);
        ReflectionTestUtils.setField(controller, "apiInterfaceService", apiInterfaceService);
        ReflectionTestUtils.setField(controller, "connectorRuntimeService", connectorRuntimeService);

        Map<String, Object> result = controller.query(Map.of(
                "vendorCode", "VENDOR_A",
                "dataTypeCode", "PERSONAL",
                "interfaceCode", "missing-interface"));

        assertEquals("INTERFACE_NOT_FOUND", result.get("errorCode"));
        verify(vendorConfigMapper, never()).selectOne(any());
    }

    @Test
    void shouldQueryEnumBackedStatusesByPersistedCode() {
        VendorInfo vendor = new VendorInfo();
        vendor.setId(1L);
        DataType dataType = new DataType();
        dataType.setId(2L);
        when(vendorInfoMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<VendorInfo> wrapper = invocation.getArgument(0);
            assertEquals(true, wrapper.getCustomSqlSegment().contains("status"));
            assertEquals(true, wrapper.getParamNameValuePairs().containsValue("active"));
            return vendor;
        });
        when(dataTypeMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<DataType> wrapper = invocation.getArgument(0);
            assertEquals(true, wrapper.getCustomSqlSegment().contains("status"));
            assertEquals(true, wrapper.getParamNameValuePairs().containsValue("active"));
            return dataType;
        });
        when(vendorConfigMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<VendorConfig> wrapper = invocation.getArgument(0);
            assertEquals(true, wrapper.getCustomSqlSegment().contains("status"));
            assertEquals(true, wrapper.getParamNameValuePairs().containsValue("active"));
            return null;
        });

        DataQueryController controller = new DataQueryController();
        ReflectionTestUtils.setField(controller, "vendorInfoMapper", vendorInfoMapper);
        ReflectionTestUtils.setField(controller, "dataTypeMapper", dataTypeMapper);
        ReflectionTestUtils.setField(controller, "vendorConfigMapper", vendorConfigMapper);
        ReflectionTestUtils.setField(controller, "apiInterfaceService", apiInterfaceService);
        ReflectionTestUtils.setField(controller, "connectorRuntimeService", connectorRuntimeService);

        Map<String, Object> result = controller.query(Map.of(
                "vendorCode", "VENDOR_A",
                "dataTypeCode", "PERSONAL"));

        assertEquals("CONFIG_NOT_FOUND", result.get("errorCode"));
    }

    @Test
    void shouldExecutePublishedConnectorSnapshot() {
        VendorInfo vendor = new VendorInfo();
        vendor.setId(1L);
        DataType dataType = new DataType();
        dataType.setId(2L);
        VendorConfig config = new VendorConfig();
        config.setId(3L);
        when(vendorInfoMapper.selectOne(any())).thenReturn(vendor);
        when(dataTypeMapper.selectOne(any())).thenReturn(dataType);
        when(vendorConfigMapper.selectOne(any())).thenReturn(config);
        when(connectorRuntimeService.execute(3L, Map.of("name", "Alice")))
                .thenReturn(Map.of("success", true, "runtimeMode", "PLUGIN"));

        DataQueryController controller = new DataQueryController();
        ReflectionTestUtils.setField(controller, "vendorInfoMapper", vendorInfoMapper);
        ReflectionTestUtils.setField(controller, "dataTypeMapper", dataTypeMapper);
        ReflectionTestUtils.setField(controller, "vendorConfigMapper", vendorConfigMapper);
        ReflectionTestUtils.setField(controller, "apiInterfaceService", apiInterfaceService);
        ReflectionTestUtils.setField(controller, "connectorRuntimeService", connectorRuntimeService);

        Map<String, Object> result = controller.query(Map.of(
                "vendorCode", "VENDOR_A",
                "dataTypeCode", "PERSONAL",
                "params", Map.of("name", "Alice")));

        assertEquals(true, result.get("success"));
        assertEquals("PLUGIN", result.get("runtimeMode"));
        verify(connectorRuntimeService).execute(3L, Map.of("name", "Alice"));
    }
}
