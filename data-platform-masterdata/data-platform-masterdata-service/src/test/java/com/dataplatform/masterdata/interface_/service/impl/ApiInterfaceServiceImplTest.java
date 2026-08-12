package com.dataplatform.masterdata.interface_.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorVersionMapper;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApiInterfaceServiceImplTest {

    private final VendorConfigMapper vendorConfigMapper = mock(VendorConfigMapper.class);
    private final VendorConnectorVersionMapper connectorVersionMapper = mock(VendorConnectorVersionMapper.class);
    private ApiInterfaceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.spy(new ApiInterfaceServiceImpl());
        ReflectionTestUtils.setField(service, "vendorConfigMapper", vendorConfigMapper);
        ReflectionTestUtils.setField(service, "connectorVersionMapper", connectorVersionMapper);
    }

    @Test
    void allowsActivationWhenPrimaryIsReadyAndFallbackIsNotReady() {
        ApiInterface apiInterface = new ApiInterface();
        apiInterface.setId(1L);
        apiInterface.setPrimaryVendorConfigId(100L);
        apiInterface.setFallbackVendorConfigId(200L);
        doReturn(apiInterface).when(service).getById(1L);

        VendorConfig primary = activePluginConfig(100L, 1000L);
        when(vendorConfigMapper.selectById(100L)).thenReturn(primary);
        when(connectorVersionMapper.selectById(1000L)).thenReturn(activeVersion(100L, 1000L));

        assertTrue(service.canActivate(1L));
    }

    @Test
    void rejectsActivationWhenPrimaryIsNotReady() {
        ApiInterface apiInterface = new ApiInterface();
        apiInterface.setId(1L);
        apiInterface.setPrimaryVendorConfigId(100L);
        apiInterface.setFallbackVendorConfigId(200L);
        doReturn(apiInterface).when(service).getById(1L);
        when(vendorConfigMapper.selectById(100L)).thenReturn(new VendorConfig());

        assertFalse(service.canActivate(1L));
    }

    private VendorConfig activePluginConfig(Long id, Long versionId) {
        VendorConfig config = new VendorConfig();
        config.setId(id);
        config.setStatus(CommonStatus.ACTIVE);
        config.setRuntimeMode("PLUGIN");
        config.setActiveConnectorVersionId(versionId);
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
