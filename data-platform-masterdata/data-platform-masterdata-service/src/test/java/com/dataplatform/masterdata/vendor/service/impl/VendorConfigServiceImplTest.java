package com.dataplatform.masterdata.vendor.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorVersionMapper;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.entity.DataType;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.mapper.ApiInterfaceMapper;
import com.dataplatform.masterdata.vendor.mapper.DataTypeMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorInfoMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VendorConfigServiceImplTest {

    private final VendorConfigMapper vendorConfigMapper = mock(VendorConfigMapper.class);
    private final VendorConnectorVersionMapper connectorVersionMapper = mock(VendorConnectorVersionMapper.class);
    private final VendorInfoMapper vendorInfoMapper = mock(VendorInfoMapper.class);
    private final DataTypeMapper dataTypeMapper = mock(DataTypeMapper.class);
    private final ApiInterfaceMapper apiInterfaceMapper = mock(ApiInterfaceMapper.class);
    private VendorConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VendorConfigServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", vendorConfigMapper);
        ReflectionTestUtils.setField(service, "connectorVersionMapper", connectorVersionMapper);
        ReflectionTestUtils.setField(service, "vendorInfoMapper", vendorInfoMapper);
        ReflectionTestUtils.setField(service, "dataTypeMapper", dataTypeMapper);
        ReflectionTestUtils.setField(service, "apiInterfaceMapper", apiInterfaceMapper);
    }

    @Test
    void refusesToBindAnInactiveVendorBeforeWriting() {
        VendorInfo vendor = new VendorInfo();
        vendor.setId(7L);
        vendor.setStatus(CommonStatus.INACTIVE);
        when(vendorInfoMapper.selectById(7L)).thenReturn(vendor);

        VendorConfig request = bindingRequest(7L, 11L, 21L);

        var exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.createBinding(request));

        assertEquals("厂商不存在或未启用", exception.getMessage());
        verify(apiInterfaceMapper, never()).selectById(11L);
        verify(vendorConfigMapper, never()).insert(org.mockito.ArgumentMatchers.any(VendorConfig.class));
    }

    @Test
    void refusesToBindAnInactiveDataTypeBeforeWriting() {
        VendorInfo vendor = new VendorInfo();
        vendor.setId(7L);
        vendor.setStatus(CommonStatus.ACTIVE);
        when(vendorInfoMapper.selectById(7L)).thenReturn(vendor);

        ApiInterface apiInterface = new ApiInterface();
        apiInterface.setId(11L);
        apiInterface.setDataTypeId(21L);
        when(apiInterfaceMapper.selectById(11L)).thenReturn(apiInterface);

        DataType dataType = new DataType();
        dataType.setId(21L);
        dataType.setStatus(CommonStatus.INACTIVE);
        when(dataTypeMapper.selectById(21L)).thenReturn(dataType);

        var exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.createBinding(bindingRequest(7L, 11L, 21L)));

        assertEquals("数据类型不存在或未启用", exception.getMessage());
        verify(vendorConfigMapper, never()).insert(org.mockito.ArgumentMatchers.any(VendorConfig.class));
    }

    @Test
    void treatsUnpublishedPrimaryAndFallbackConfigsAsNotReadyWithoutEmptyBatchQuery() {
        VendorConfig primary = unpublishedPluginConfig(101L);
        VendorConfig fallback = unpublishedPluginConfig(102L);
        when(vendorConfigMapper.selectBatchIds(List.of(101L, 102L))).thenReturn(List.of(primary, fallback));

        Map<Long, Boolean> result = service.canActivateConfigs(List.of(101L, 102L));

        assertEquals(Map.of(101L, false, 102L, false), result);
        verify(connectorVersionMapper, never()).selectBatchIds(List.of());
    }

    @Test
    void allowsInactiveConfigToActivateWhenItsConnectorVersionIsPublished() {
        VendorConfig config = unpublishedPluginConfig(3L);
        config.setStatus(CommonStatus.INACTIVE);
        config.setActiveConnectorVersionId(5L);
        when(vendorConfigMapper.selectById(3L)).thenReturn(config);
        when(connectorVersionMapper.selectById(5L)).thenReturn(activeVersion(3L, 5L));

        assertTrue(service.canActivate(3L));
    }

    @Test
    void keepsRouteReadinessStrictWhenInactiveConfigsHavePublishedVersions() {
        VendorConfig primary = unpublishedPluginConfig(3L);
        primary.setStatus(CommonStatus.INACTIVE);
        primary.setActiveConnectorVersionId(5L);
        VendorConfig fallback = unpublishedPluginConfig(4L);
        fallback.setStatus(CommonStatus.INACTIVE);
        fallback.setActiveConnectorVersionId(6L);
        when(vendorConfigMapper.selectBatchIds(List.of(3L, 4L))).thenReturn(List.of(primary, fallback));
        when(connectorVersionMapper.selectBatchIds(List.of(5L, 6L)))
                .thenReturn(List.of(activeVersion(3L, 5L), activeVersion(4L, 6L)));

        Map<Long, Boolean> result = service.canActivateConfigs(List.of(3L, 4L));

        assertFalse(result.get(3L));
        assertFalse(result.get(4L));
    }

    private VendorConfig unpublishedPluginConfig(Long id) {
        VendorConfig config = new VendorConfig();
        config.setId(id);
        config.setStatus(CommonStatus.ACTIVE);
        config.setRuntimeMode("PLUGIN");
        config.setActiveConnectorVersionId(null);
        return config;
    }

    private VendorConfig bindingRequest(Long vendorId, Long interfaceId, Long dataTypeId) {
        VendorConfig config = new VendorConfig();
        config.setVendorId(vendorId);
        config.setInterfaceId(interfaceId);
        config.setDataTypeId(dataTypeId);
        return config;
    }

    private VendorConnectorVersion activeVersion(Long configId, Long versionId) {
        VendorConnectorVersion version = new VendorConnectorVersion();
        version.setId(versionId);
        version.setVendorConfigId(configId);
        version.setStatus("ACTIVE");
        return version;
    }
}
