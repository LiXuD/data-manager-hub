package com.dataplatform.common.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GenericHttpSeedMigrationContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void v050SeedIsCryptographicallyBoundToTheSingleRuntimeMetadataFact() throws Exception {
        String migration = Files.readString(repositoryRoot().resolve(
                "sql/migrations/V050__seed_generic_http_connector_v2.sql"));
        var matcher = Pattern.compile("\\$manifest\\$(.*?)\\$manifest\\$", Pattern.DOTALL)
                .matcher(migration);
        assertTrue(matcher.find(), "V050 must expose one verifiable manifest literal");
        String seededManifestJson = matcher.group(1);
        assertFalse(matcher.find(), "V050 must not maintain duplicate manifest literals");

        JsonNode seededManifest = MAPPER.readTree(seededManifestJson);
        JsonNode runtimeManifest = MAPPER.readTree(
                GenericHttpConnectorMetadata.canonicalManifestJson());
        assertEquals(runtimeManifest, seededManifest);
        assertEquals(GenericHttpConnectorMetadata.configSchema(),
                seededManifest.path("configSchema"));
        assertEquals(MAPPER.readTree(GenericHttpConnectorMetadata.canonicalPermissionsJson()),
                seededManifest.path("permissions"));
        assertEquals(MAPPER.readTree(GenericHttpConnectorMetadata.canonicalCompatibilityJson()),
                seededManifest.path("compatibility"));
        assertEquals(GenericHttpConnectorMetadata.CAPABILITY_NAMES,
                MAPPER.convertValue(seededManifest.path("capabilities"),
                        MAPPER.getTypeFactory().constructCollectionType(java.util.List.class, String.class)));

        var reader = new PluginManifestReader(MAPPER);
        assertEquals(GenericHttpConnectorMetadata.canonicalManifestJson(), new String(
                reader.canonicalize(MAPPER.writeValueAsBytes(seededManifest)),
                StandardCharsets.UTF_8));
        assertEquals(GenericHttpConnectorMetadata.artifactSha256(),
                exactLiteral(migration, GenericHttpConnectorMetadata.artifactSha256()));
        assertTrue(migration.contains(GenericHttpConnectorMetadata.manifestSha256()));
        assertTrue(migration.contains(GenericHttpConnectorMetadata.schemaSha256()));
        assertTrue(migration.contains("ON CONFLICT (plugin_id) DO NOTHING"));
        assertTrue(migration.contains("ON CONFLICT (plugin_id, version) DO NOTHING"));
        assertFalse(migration.matches("(?is).*ON\\s+CONFLICT.*DO\\s+UPDATE.*"));
        assertFalse(migration.matches("(?im)^\\s*UPDATE\\s+(connector_plugin|connector_plugin_version)\\b.*"));

        GenericHttpConnectorConfigValidator.validate(MAPPER.valueToTree(Map.of(
                "endpoint", "https://vendor.example/api",
                "method", "GET",
                "auth", Map.of("type", "NONE"))), ignored -> false);
    }

    @Test
    void u050BindsExactHashesAndGuardsEveryReferenceSurface() throws Exception {
        String rollback = Files.readString(repositoryRoot().resolve(
                "sql/rollbacks/U050__seed_generic_http_connector_v2.sql"));
        assertTrue(rollback.contains(GenericHttpConnectorMetadata.artifactSha256()));
        var manifestMatcher = Pattern.compile("\\$manifest\\$(.*?)\\$manifest\\$", Pattern.DOTALL)
                .matcher(rollback);
        assertTrue(manifestMatcher.find());
        assertEquals(MAPPER.readTree(GenericHttpConnectorMetadata.canonicalManifestJson()),
                MAPPER.readTree(manifestMatcher.group(1)));
        assertFalse(rollback.contains("CREATE OR REPLACE FUNCTION v050_rollback"));
        for (String table : java.util.List.of(
                "vendor_connector_version", "vendor_connector_test_fact",
                "connector_plugin_activation", "call_record", "billing_event")) {
            assertTrue(rollback.contains(table), "missing rollback reference gate: " + table);
        }
        assertTrue(rollback.contains("DISABLE TRIGGER trg_connector_plugin_version_immutable"));
        assertTrue(rollback.contains("ENABLE TRIGGER trg_connector_plugin_version_immutable"));
        assertTrue(rollback.contains("DISABLE TRIGGER trg_connector_plugin_reject_delete"));
        assertTrue(rollback.contains("ENABLE TRIGGER trg_connector_plugin_reject_delete"));
    }

    private String exactLiteral(String source, String expected) {
        assertTrue(source.contains("'" + expected + "'"));
        return expected;
    }

    private Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("sql/migrations"))
                    && Files.isDirectory(current.resolve("data-platform-common-runtime"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root cannot be located");
    }
}
