package com.dataplatform.masterdata.connector.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConnectorPluginOnlyMigrationContractTest {

    @Test
    void seedBypassRejectsBothLegacyParameterMappingRepresentations() throws IOException {
        String migration = Files.readString(findMigration());

        assertTrue(migration.contains(
                "COALESCE(vc.param_mapping, '[]'::JSONB) = '[]'::JSONB"));
        assertTrue(migration.contains("SELECT 1 FROM vendor_params_mapping mapping"));
        assertTrue(migration.contains("WHERE mapping.vendor_config_id = vc.id"));
    }

    private Path findMigration() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(
                    "sql/migrations/V044__enforce_plugin_only_vendor_runtime.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("V044 migration not found from test working directory");
    }
}
