package com.dataplatform.masterdata.connector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorExtendedConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import java.util.List;
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
        VendorExtendedConfig encrypted = new VendorExtendedConfig();
        encrypted.setConfigKey("vendor.token");
        encrypted.setIsEncrypted(true);
        encrypted.setConfigValue("••••••••");
        when(extended.getByVendor(70L)).thenReturn(List.of(encrypted));
        ConnectorSecretReferenceService service = new ConnectorSecretReferenceService(
                configMapper, vendorMapper, extended);

        assertEquals(Map.of("vendor.secretKey", "vendor-secret", "vendor.token", "token-value"),
                service.resolve(7L, Set.of("vendor.secretKey", "vendor.token")));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(7L, Set.of("other-vendor.privateKey")));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(999L, Set.of("vendor.token")));
    }

    @Test
    void refusesPlaintextExtendedConfigurationAsASecretReference() {
        VendorConfigMapper configMapper = mock(VendorConfigMapper.class);
        VendorInfoMapper vendorMapper = mock(VendorInfoMapper.class);
        VendorExtendedConfigService extended = mock(VendorExtendedConfigService.class);
        VendorConfig config = new VendorConfig();
        config.setId(7L);
        config.setVendorId(70L);
        VendorInfo vendor = new VendorInfo();
        vendor.setId(70L);
        VendorExtendedConfig plain = new VendorExtendedConfig();
        plain.setConfigKey("vendor.endpoint");
        plain.setIsEncrypted(false);
        plain.setConfigValue("https://vendor.example");
        when(configMapper.selectById(7L)).thenReturn(config);
        when(vendorMapper.selectById(70L)).thenReturn(vendor);
        when(extended.getByVendor(70L)).thenReturn(List.of(plain));

        ConnectorSecretReferenceService service = new ConnectorSecretReferenceService(
                configMapper, vendorMapper, extended);

        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(7L, Set.of("vendor.endpoint")));
    }

    @Test
    void listsReferenceMetadataWithoutReturningSecretValues() {
        VendorConfigMapper configMapper = mock(VendorConfigMapper.class);
        VendorInfoMapper vendorMapper = mock(VendorInfoMapper.class);
        VendorExtendedConfigService extended = mock(VendorExtendedConfigService.class);
        VendorConfig config = new VendorConfig();
        config.setId(7L);
        config.setVendorId(70L);
        VendorInfo vendor = new VendorInfo();
        vendor.setId(70L);
        vendor.setSecretKey("do-not-return");
        VendorExtendedConfig encrypted = new VendorExtendedConfig();
        encrypted.setConfigKey("vendor.token");
        encrypted.setIsEncrypted(true);
        encrypted.setConfigValue("••••••••");
        VendorExtendedConfig plain = new VendorExtendedConfig();
        plain.setConfigKey("vendor.endpoint");
        plain.setIsEncrypted(false);
        when(configMapper.selectById(7L)).thenReturn(config);
        when(vendorMapper.selectById(70L)).thenReturn(vendor);
        when(extended.getByVendor(70L)).thenReturn(List.of(encrypted, plain));

        var options = new ConnectorSecretReferenceService(configMapper, vendorMapper, extended)
                .listAvailableReferences(7L);

        assertEquals(List.of("vendor.secretKey", "vendor.token"),
                options.stream().map(option -> option.secretRef()).toList());
        assertEquals(List.of("VENDOR", "VENDOR"),
                options.stream().map(option -> option.scope()).toList());
        assertEquals(List.of(true, true), options.stream().map(option -> option.available()).toList());
    }
}
