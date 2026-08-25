package com.dataplatform.masterdata.connector.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dataplatform.access.connector.api.feign.ConnectorPluginActivationInternalFeignClient;
import com.dataplatform.masterdata.connector.api.dto.PluginImportRequestDTO;
import com.dataplatform.masterdata.connector.entity.ConnectorPlugin;
import com.dataplatform.masterdata.connector.entity.ConnectorPluginVersion;
import com.dataplatform.masterdata.connector.mapper.ConnectorPluginMapper;
import com.dataplatform.masterdata.connector.mapper.ConnectorPluginVersionMapper;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorTestFactMapper;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorVersionMapper;
import com.dataplatform.masterdata.connector.service.ConnectorConflictException;
import com.dataplatform.masterdata.connector.service.PluginArtifactVerifier;
import com.dataplatform.masterdata.connector.service.VerifiedPluginArtifact;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConnectorPluginVersionImmutabilityTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                ConnectorPluginVersion.class);
    }

    @Test
    void importingSamePluginVersionCannotOverwriteVerifiedArtifact() {
        ConnectorPluginVersionMapper versionMapper = mock(ConnectorPluginVersionMapper.class);
        ConnectorPluginVersion existing = new ConnectorPluginVersion();
        existing.setPluginId("demo-http");
        existing.setVersion("1.2.0");
        existing.setStatus("VERIFIED");
        when(versionMapper.selectOne(any())).thenReturn(existing);
        PluginArtifactVerifier verifier = mock(PluginArtifactVerifier.class);
        when(verifier.verify(any())).thenReturn(verified());
        ConnectorPluginMapper pluginMapper = mock(ConnectorPluginMapper.class);
        ConnectorPluginCatalogServiceImpl service = new ConnectorPluginCatalogServiceImpl(
                pluginMapper, versionMapper, mock(VendorConnectorVersionMapper.class),
                mock(com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper.class),
                mock(VendorConnectorTestFactMapper.class), verifier,
                mock(ConnectorPluginActivationInternalFeignClient.class),
                mock(com.dataplatform.masterdata.connector.service.ConnectorPluginReleaseCoordinator.class),
                new ObjectMapper());

        assertThrows(ConnectorConflictException.class, () -> service.importVersion(
                new PluginImportRequestDTO("https://repo.example/plugins/demo.jar", "a".repeat(64),
                        "signature", "release-key"), 7L));

        verify(versionMapper, never()).insert(any(ConnectorPluginVersion.class));
        verify(pluginMapper, never()).insert(any(ConnectorPlugin.class));
    }

    private VerifiedPluginArtifact verified() {
        return new VerifiedPluginArtifact("demo-http", "1.2.0", "1.0", "Demo", "internal", null,
                "example.DemoPlugin", "https://repo.example/plugins/demo.jar", "a".repeat(64),
                "signature", "release-key", "{}", "{}", List.of("TRANSPORT"), "{}",
                "2.1.0", new ObjectMapper().createObjectNode());
    }
}
