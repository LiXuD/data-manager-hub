package com.dataplatform.masterdata.connector.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorConfigValidator;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorMetadata;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDTO;
import com.dataplatform.masterdata.connector.service.VerifiedPluginArtifact;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class GenericHttpSeedDatabaseAcceptanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void realSeedRowPassesManifestRuntimeValidatorAndDeterministicCompiler() throws Exception {
        String jdbcUrl = System.getProperty("v050.jdbcUrl");
        Assumptions.assumeTrue(jdbcUrl != null && !jdbcUrl.isBlank());
        String username = System.getProperty("v050.db.username", "postgres");
        String password = System.getenv("DB_PASSWORD");

        try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
             var statement = connection.prepareStatement("""
                     SELECT parent.display_name, parent.provider, parent.description,
                            version.plugin_id, version.version, version.spi_version,
                            version.entry_class, version.artifact_uri, btrim(version.artifact_sha256),
                            version.detached_signature, version.signing_key_id,
                            version.manifest_json::text, version.config_schema_json::text,
                            version.capabilities::text, version.permission_manifest::text,
                            version.min_host_version, version.status, version.manifest_version,
                            version.authoring_model, version.connector_kind,
                            version.transport_mode, version.output_mode,
                            version.compatibility_manifest::text
                     FROM connector_plugin parent
                     JOIN connector_plugin_version version USING (plugin_id)
                     WHERE version.plugin_id = 'generic-http' AND version.version = '2.0.0'
                     """);
             var rows = statement.executeQuery()) {
            assertFalse(connection.isClosed());
            if (!rows.next()) throw new AssertionError("V050 generic-http row is missing");
            String manifestJson = rows.getString(12);
            String schemaJson = rows.getString(13);
            var reader = new PluginManifestReader(MAPPER);
            var manifest = reader.read(manifestJson.getBytes(StandardCharsets.UTF_8));
            String canonicalCompatibility = new String(reader.canonicalize(MAPPER.writeValueAsBytes(
                    MAPPER.readTree(manifestJson).path("compatibility"))), StandardCharsets.UTF_8);
            List<String> capabilities = MAPPER.readValue(rows.getString(14), new TypeReference<>() {});
            var artifact = new VerifiedPluginArtifact(
                    rows.getString(4), rows.getString(5), rows.getString(6),
                    rows.getString(1), rows.getString(2), rows.getString(3),
                    rows.getString(7), rows.getString(8), rows.getString(9),
                    rows.getString(10), rows.getString(11), manifestJson, schemaJson,
                    capabilities, rows.getString(15), rows.getString(16),
                    MAPPER.readTree(schemaJson), rows.getString(18), manifest.authoringModel(),
                    manifest.connectorKind(), manifest.transportMode(), manifest.outputMode(),
                    manifest.compatibility(), canonicalCompatibility);

            var config = Map.<String, Object>of(
                    "endpoint", "https://vendor.example/api",
                    "method", "GET",
                    "auth", Map.of("type", "NONE"));
            GenericHttpConnectorConfigValidator.validate(MAPPER.valueToTree(config), ignored -> false);
            var spec = new ConnectorSpecDTO("1", new ConnectorSpecDTO.PluginRef(
                    GenericHttpConnectorMetadata.PLUGIN_ID,
                    GenericHttpConnectorMetadata.VERSION), config, null);
            var result = new ConnectorSpecCompiler(MAPPER).compile(new ConnectorSpecCompilationInput(
                    1L, "ANY_VENDOR", "ANY_TYPE", spec, artifact,
                    ConnectorPluginCatalogStatus.valueOf(rows.getString(17)), 0L,
                    List.of(), ignored -> false, ConnectorCompilationPurpose.VALIDATE));
            assertEquals(GenericHttpConnectorMetadata.PLUGIN_ID,
                    result.pipelineSteps().getFirst().pluginId());
            assertEquals(64, result.specHash().length());
            assertEquals(64, result.snapshotHash().length());
            assertEquals(64, result.compileHash().length());
        }
    }
}
