package com.dataplatform.access.connector.runtime;

import com.dataplatform.api.Result;
import com.dataplatform.common.plugin.artifact.PluginManifest;
import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.dataplatform.common.plugin.runtime.ConnectorPluginMetadata;
import com.dataplatform.common.plugin.runtime.ConnectorPluginMetadataResolver;
import com.dataplatform.common.plugin.runtime.ConnectorSnapshotIntegrity;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import com.dataplatform.masterdata.connector.api.feign.ConnectorPluginInternalFeignClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Resolves immutable control-plane metadata and rejects descriptor/Manifest drift. */
@Component
public class MasterdataConnectorPluginMetadataResolver implements ConnectorPluginMetadataResolver {

    private final ConnectorPluginInternalFeignClient client;
    private final ObjectMapper mapper;
    private final PluginManifestReader manifestReader;

    public MasterdataConnectorPluginMetadataResolver(
            ConnectorPluginInternalFeignClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
        this.manifestReader = new PluginManifestReader(mapper);
    }

    @Override
    public ConnectorPluginMetadata resolve(String pluginId, String version) {
        Result<PluginArtifactDescriptorDTO> response = client.getArtifact(pluginId, version);
        PluginArtifactDescriptorDTO artifact = response != null ? response.getData() : null;
        if (artifact == null) throw new IllegalStateException("Fixed plugin metadata is unavailable");
        return validate(artifact, pluginId, version);
    }

    public ConnectorPluginMetadata validate(PluginArtifactDescriptorDTO artifact,
                                            String expectedPluginId, String expectedVersion) {
        try {
            if (!expectedPluginId.equals(artifact.pluginId()) || !expectedVersion.equals(artifact.version())
                    || !isSha256(artifact.artifactSha256())
                    || !StringUtils.hasText(artifact.manifestJson())
                    || !StringUtils.hasText(artifact.configSchemaJson())) {
                throw new IllegalStateException("Plugin artifact metadata is incomplete or mismatched");
            }
            byte[] manifestBytes = artifact.manifestJson().getBytes(StandardCharsets.UTF_8);
            JsonNode manifestDocument = mapper.readTree(manifestBytes);
            JsonNode strictManifest = manifestDocument.deepCopy();
            if (strictManifest.isObject()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) strictManifest).remove("description");
            }
            PluginManifest manifest = manifestReader.read(mapper.writeValueAsBytes(strictManifest));
            JsonNode descriptorSchema = mapper.readTree(artifact.configSchemaJson());
            if (!artifact.pluginId().equals(manifest.pluginId())
                    || !artifact.version().equals(manifest.version())
                    || !artifact.entryClass().equals(manifest.entryClass())
                    || !Set.copyOf(artifact.capabilities()).equals(manifest.capabilities().stream()
                            .map(Enum::name).collect(java.util.stream.Collectors.toSet()))
                    || !ConnectorSnapshotIntegrity.sha256(mapper, descriptorSchema)
                    .equals(ConnectorSnapshotIntegrity.sha256(mapper, manifest.configSchema()))) {
                throw new IllegalStateException("Plugin descriptor, Manifest and Schema are not bound");
            }
            JsonNode canonicalManifest = mapper.readTree(manifestReader.canonicalize(manifestBytes));
            return new ConnectorPluginMetadata(artifact.pluginId(), artifact.version(),
                    artifact.artifactSha256().toLowerCase(Locale.ROOT),
                    ConnectorSnapshotIntegrity.sha256(mapper, canonicalManifest),
                    ConnectorSnapshotIntegrity.sha256(mapper, descriptorSchema), descriptorSchema);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Plugin metadata cannot be verified", exception);
        }
    }

    private boolean isSha256(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{64}");
    }
}
