package com.dataplatform.access.connector.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.api.Result;
import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorMetadata;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import com.dataplatform.masterdata.connector.api.feign.ConnectorPluginInternalFeignClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class GenericHttpSeedDatabaseAcceptanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void realSeedProjectionPassesTheAccessBuiltinDriftGate() throws Exception {
        String jdbcUrl = System.getProperty("v050.jdbcUrl");
        Assumptions.assumeTrue(jdbcUrl != null && !jdbcUrl.isBlank());
        String username = System.getProperty("v050.db.username", "postgres");
        String password = System.getenv("DB_PASSWORD");

        PluginArtifactDescriptorDTO artifact;
        try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
             var statement = connection.prepareStatement("""
                     SELECT plugin_id, version, spi_version, entry_class, artifact_uri,
                            btrim(artifact_sha256), detached_signature, signing_key_id,
                            manifest_json::text, config_schema_json::text, capabilities::text,
                            permission_manifest::text, min_host_version, status,
                            manifest_version, authoring_model, connector_kind,
                            transport_mode, output_mode, compatibility_manifest::text
                     FROM connector_plugin_version
                     WHERE plugin_id = 'generic-http' AND version = '2.0.0'
                     """);
             var rows = statement.executeQuery()) {
            if (!rows.next()) throw new AssertionError("V050 generic-http row is missing");
            List<String> capabilities = MAPPER.readValue(rows.getString(11), new TypeReference<>() {});
            String manifestJson = rows.getString(9);
            var reader = new PluginManifestReader(MAPPER);
            String canonicalCompatibility = new String(reader.canonicalize(MAPPER.writeValueAsBytes(
                    MAPPER.readTree(manifestJson).path("compatibility"))), StandardCharsets.UTF_8);
            artifact = new PluginArtifactDescriptorDTO(
                    rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4),
                    rows.getString(5), rows.getString(6), rows.getString(7), rows.getString(8),
                    manifestJson, rows.getString(10), capabilities, rows.getString(12),
                    rows.getString(13), rows.getString(14), rows.getString(15), rows.getString(16),
                    rows.getString(17), rows.getString(18), rows.getString(19), canonicalCompatibility);
        }

        ConnectorPluginInternalFeignClient client = mock(ConnectorPluginInternalFeignClient.class);
        when(client.getArtifact(GenericHttpConnectorMetadata.PLUGIN_ID,
                GenericHttpConnectorMetadata.VERSION)).thenReturn(Result.success(artifact));
        var resolver = new MasterdataConnectorPluginMetadataResolver(client, MAPPER);
        assertEquals(GenericHttpConnectorMetadata.metadata(), resolver.resolve(
                GenericHttpConnectorMetadata.PLUGIN_ID, GenericHttpConnectorMetadata.VERSION));
    }
}
