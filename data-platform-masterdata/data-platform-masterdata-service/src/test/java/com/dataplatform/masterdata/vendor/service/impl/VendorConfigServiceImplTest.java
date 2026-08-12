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
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VendorConfigServiceImplTest {

    private final VendorConfigMapper vendorConfigMapper = mock(VendorConfigMapper.class);
    private final VendorConnectorVersionMapper connectorVersionMapper = mock(VendorConnectorVersionMapper.class);
    private VendorConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VendorConfigServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", vendorConfigMapper);
        ReflectionTestUtils.setField(service, "connectorVersionMapper", connectorVersionMapper);
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

    private VendorConnectorVersion activeVersion(Long configId, Long versionId) {
        VendorConnectorVersion version = new VendorConnectorVersion();
        version.setId(versionId);
        version.setVendorConfigId(configId);
        version.setStatus("ACTIVE");
        return version;
    }
}
