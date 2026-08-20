package com.dataplatform.access.connector.runtime;

import com.dataplatform.api.Result;
import com.dataplatform.common.plugin.artifact.PluginManifest;
import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.dataplatform.common.plugin.runtime.ConnectorPluginMetadata;
import com.dataplatform.common.plugin.runtime.ConnectorPluginMetadataResolver;
import com.dataplatform.common.plugin.runtime.ConnectorSnapshotIntegrity;
import com.dataplatform.common.plugin.runtime.GenericHttpConnectorMetadata;
import com.dataplatform.common.plugin.runtime.PlatformCoreConnectorMetadata;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import com.dataplatform.masterdata.connector.api.feign.ConnectorPluginInternalFeignClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
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
        if (PlatformCoreConnectorMetadata.PLUGIN_ID.equals(pluginId)) {
            if (!PlatformCoreConnectorMetadata.VERSION.equals(version)) {
                throw new IllegalStateException("Unsupported platform-core plugin version");
            }
            return PlatformCoreConnectorMetadata.metadata();
        }
        if (GenericHttpConnectorMetadata.PLUGIN_ID.equals(pluginId)
                && !GenericHttpConnectorMetadata.VERSION.equals(version)) {
            throw new IllegalStateException("Unsupported generic-http plugin version");
        }
        Result<PluginArtifactDescriptorDTO> response = client.getArtifact(pluginId, version);
        PluginArtifactDescriptorDTO artifact = response != null ? response.getData() : null;
        if (artifact == null) throw new IllegalStateException("Fixed plugin metadata is unavailable");
        ConnectorPluginMetadata metadata = validate(artifact, pluginId, version);
        if (GenericHttpConnectorMetadata.PLUGIN_ID.equals(pluginId)) {
            validateGenericBuiltin(artifact, metadata);
        }
        return metadata;
    }

    /** Revalidates the catalogue projection before any built-in READY fast path is trusted. */
    public ConnectorPluginMetadata validateGenericBuiltin(PluginArtifactDescriptorDTO artifact) {
        if (artifact == null || !GenericHttpConnectorMetadata.PLUGIN_ID.equals(artifact.pluginId())
                || !GenericHttpConnectorMetadata.VERSION.equals(artifact.version())) {
            throw new IllegalStateException("Generic HTTP built-in coordinates mismatch");
        }
        ConnectorPluginMetadata metadata = validate(
                artifact, GenericHttpConnectorMetadata.PLUGIN_ID, GenericHttpConnectorMetadata.VERSION);
        validateGenericBuiltin(artifact, metadata);
        return metadata;
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
            PluginManifest manifest = manifestReader.read(manifestBytes);
            JsonNode descriptorSchema = mapper.readTree(artifact.configSchemaJson());
            if (!artifact.pluginId().equals(manifest.pluginId())
                    || !artifact.version().equals(manifest.version())
                    || !Objects.equals(artifact.spiVersion(), manifest.spiVersion())
                    || !Objects.equals(artifact.minHostVersion(), manifest.minHostVersion())
                    || !artifact.entryClass().equals(manifest.entryClass())
                    || !Set.copyOf(artifact.capabilities()).equals(manifest.capabilities().stream()
                            .map(Enum::name).collect(java.util.stream.Collectors.toSet()))
                    || !ConnectorSnapshotIntegrity.sha256(mapper, descriptorSchema)
                    .equals(ConnectorSnapshotIntegrity.sha256(mapper, manifest.configSchema()))) {
                throw new IllegalStateException("Plugin descriptor, Manifest and Schema are not bound");
            }
            validateProjection(artifact, manifest, manifestDocument);
            JsonNode canonicalManifest = mapper.readTree(manifestReader.canonicalize(manifestBytes));
            return new ConnectorPluginMetadata(artifact.pluginId(), artifact.version(),
                    artifact.artifactSha256().toLowerCase(Locale.ROOT),
                    ConnectorSnapshotIntegrity.sha256(mapper, canonicalManifest),
                    ConnectorSnapshotIntegrity.sha256(mapper, descriptorSchema), descriptorSchema,
                    manifest.manifestVersion(), manifest.authoringModel(), manifest.connectorKind(),
                    manifest.transportMode(), manifest.outputMode(), manifest.compatibility());
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Plugin metadata cannot be verified", exception);
        }
    }

    private boolean isSha256(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{64}");
    }

    private void validateProjection(
            PluginArtifactDescriptorDTO artifact,
            PluginManifest manifest,
            JsonNode manifestDocument) throws Exception {
        String expectedCompatibility = "2".equals(manifest.manifestVersion())
                ? new String(manifestReader.canonicalize(mapper.writeValueAsBytes(
                manifestDocument.path("compatibility"))), StandardCharsets.UTF_8)
                : "{}";
        String expectedPermissions = new String(manifestReader.canonicalize(
                mapper.writeValueAsBytes(manifestDocument.path("permissions"))), StandardCharsets.UTF_8);
        String actualPermissions = new String(manifestReader.canonicalize(
                artifact.permissionManifestJson().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        if (!Objects.equals(artifact.manifestVersion(), manifest.manifestVersion())
                || !Objects.equals(artifact.authoringModel(), manifest.authoringModel().name())
                || !Objects.equals(artifact.connectorKind(), enumName(manifest.connectorKind()))
                || !Objects.equals(artifact.transportMode(), enumName(manifest.transportMode()))
                || !Objects.equals(artifact.outputMode(), enumName(manifest.outputMode()))
                || !Objects.equals(artifact.compatibilityJson(), expectedCompatibility)
                || !Objects.equals(actualPermissions, expectedPermissions)) {
            throw new IllegalStateException("Plugin v2 index projection drifted from signed Manifest");
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private void validateGenericBuiltin(
            PluginArtifactDescriptorDTO artifact, ConnectorPluginMetadata metadata) {
        try {
            String canonicalManifest = new String(manifestReader.canonicalize(
                    artifact.manifestJson().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
            String canonicalSchema = new String(manifestReader.canonicalize(
                    artifact.configSchemaJson().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
            String canonicalPermissions = new String(manifestReader.canonicalize(
                    artifact.permissionManifestJson().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
            if (!GenericHttpConnectorMetadata.ENTRY_CLASS.equals(artifact.entryClass())
                    || !GenericHttpConnectorMetadata.ARTIFACT_URI.equals(artifact.artifactUri())
                    || !GenericHttpConnectorMetadata.BUILTIN_SIGNATURE.equals(artifact.detachedSignature())
                    || !GenericHttpConnectorMetadata.BUILTIN_SIGNING_KEY.equals(artifact.signingKeyId())
                    || !GenericHttpConnectorMetadata.SPI_VERSION.equals(artifact.spiVersion())
                    || !GenericHttpConnectorMetadata.MIN_HOST_VERSION.equals(artifact.minHostVersion())
                    || !GenericHttpConnectorMetadata.artifactSha256().equalsIgnoreCase(artifact.artifactSha256())
                    || !GenericHttpConnectorMetadata.canonicalManifestJson().equals(canonicalManifest)
                    || !GenericHttpConnectorMetadata.canonicalSchemaJson().equals(canonicalSchema)
                    || !GenericHttpConnectorMetadata.canonicalPermissionsJson().equals(canonicalPermissions)
                    || !GenericHttpConnectorMetadata.CAPABILITY_NAMES.equals(artifact.capabilities())
                    || !"2".equals(artifact.manifestVersion())
                    || !"SIMPLE_CONNECTOR".equals(artifact.authoringModel())
                    || !"GENERIC_HTTP".equals(artifact.connectorKind())
                    || !"HOST_SINGLE_HTTP".equals(artifact.transportMode())
                    || !"HOST_MAPPING".equals(artifact.outputMode())
                    || !GenericHttpConnectorMetadata.canonicalCompatibilityJson()
                    .equals(artifact.compatibilityJson())
                    || !GenericHttpConnectorMetadata.metadata().equals(metadata)) {
                throw new IllegalStateException("Generic HTTP built-in metadata drifted from host code");
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Generic HTTP built-in metadata cannot be verified", exception);
        }
    }
}
