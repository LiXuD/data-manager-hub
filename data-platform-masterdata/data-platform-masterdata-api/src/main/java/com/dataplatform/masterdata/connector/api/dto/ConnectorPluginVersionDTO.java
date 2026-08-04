package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public record ConnectorPluginVersionDTO(
        Long id,
        String pluginId,
        String version,
        String spiVersion,
        String entryClass,
        String artifactUri,
        String artifactSha256,
        String signingKeyId,
        String manifestJson,
        String configSchemaJson,
        List<String> capabilities,
        String permissionManifestJson,
        String minHostVersion,
        String status,
        String safeErrorCode,
        String safeErrorDigest,
        LocalDateTime verifiedAt,
        LocalDateTime createdAt) implements Serializable {
}
