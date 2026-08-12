package com.dataplatform.access.connector.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Checks connector activation storage without touching the activation mapper. */
@Component
public class ConnectorActivationSchemaGuard {

    static final String ACTIVATION_TABLE = "connector_plugin_activation";

    private final DataSource dataSource;

    public ConnectorActivationSchemaGuard(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isReady() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            return hasTable(metadata, connection.getCatalog(), connection.getSchema(), ACTIVATION_TABLE)
                    || hasTable(metadata, connection.getCatalog(), connection.getSchema(),
                    ACTIVATION_TABLE.toUpperCase());
        } catch (SQLException exception) {
            throw new IllegalStateException("CONNECTOR_SCHEMA_GUARD_FAILED", exception);
        }
    }

    private boolean hasTable(DatabaseMetaData metadata, String catalog, String schema, String tableName)
            throws SQLException {
        try (ResultSet tables = metadata.getTables(catalog, schema, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}
