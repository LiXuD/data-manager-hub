package com.dataplatform.masterdata.connector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorInfoMapper;
import com.dataplatform.masterdata.vendor.service.VendorExtendedConfigService;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConnectorSecretReferenceServiceTest {

    @Test
    void resolvesOnlySecretsOwnedByTheVendorBoundToTheConfig() {
        VendorConfigMapper configMapper = mock(VendorConfigMapper.class);
        VendorInfoMapper vendorMapper = mock(VendorInfoMapper.class);
        VendorExtendedConfigService extended = mock(VendorExtendedConfigService.class);
        VendorConfig config = new VendorConfig();
        config.setId(7L);
        config.setVendorId(70L);
        VendorInfo vendor = new VendorInfo();
        vendor.setId(70L);
        vendor.setSecretKey("vendor-secret");
        when(configMapper.selectById(7L)).thenReturn(config);
        when(vendorMapper.selectById(70L)).thenReturn(vendor);
        when(extended.getConfig(70L, "vendor.token")).thenReturn("token-value");
        ConnectorSecretReferenceService service = new ConnectorSecretReferenceService(
                configMapper, vendorMapper, extended);

        assertEquals(Map.of("vendor.secretKey", "vendor-secret", "vendor.token", "token-value"),
                service.resolve(7L, Set.of("vendor.secretKey", "vendor.token")));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(7L, Set.of("other-vendor.privateKey")));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(999L, Set.of("vendor.token")));
    }
}
