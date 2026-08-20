package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** Product-facing SIMPLE connector catalog. Raw manifests and artifact locations are never exposed. */
public record ConnectorSpecCatalogDTO(List<Entry> plugins) implements Serializable {

    public ConnectorSpecCatalogDTO {
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
    }

    public record Entry(
            String pluginId,
            String displayName,
            String provider,
            String description,
            String connectorKind,
            String transportMode,
            String outputMode,
            String recommendedVersion,
            int versionCount,
            Map<String, Object> configSchema,
            Compatibility compatibility) implements Serializable { }

    public record Version(
            String pluginId,
            String pluginVersion,
            String status,
            boolean testable,
            boolean active,
            String displayName,
            String provider,
            String description,
            String connectorKind,
            String transportMode,
            String outputMode,
            Map<String, Object> configSchema,
            Compatibility compatibility) implements Serializable { }

    public record Compatibility(List<String> vendorCodes, List<String> dataTypeCodes)
            implements Serializable {
        public Compatibility {
            vendorCodes = vendorCodes == null ? List.of() : List.copyOf(vendorCodes);
            dataTypeCodes = dataTypeCodes == null ? List.of() : List.copyOf(dataTypeCodes);
        }
    }
}
