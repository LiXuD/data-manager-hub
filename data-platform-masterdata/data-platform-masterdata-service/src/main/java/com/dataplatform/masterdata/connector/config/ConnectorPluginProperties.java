package com.dataplatform.masterdata.connector.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "masterdata.connector-plugin")
public class ConnectorPluginProperties {
    private List<String> artifactAllowedHosts = new ArrayList<>();
    private List<String> artifactAllowedPathPrefixes = new ArrayList<>(List.of("/"));
    private Map<String, String> trustedSigningKeys = new LinkedHashMap<>();
    private int maxArtifactBytes = 50 * 1024 * 1024;
    private int maxManifestBytes = 256 * 1024;
    private int maxSchemaBytes = 128 * 1024;
    private boolean legacyWriteRetired;

    public List<String> getArtifactAllowedHosts() { return artifactAllowedHosts; }
    public void setArtifactAllowedHosts(List<String> artifactAllowedHosts) {
        this.artifactAllowedHosts = artifactAllowedHosts;
    }
    public List<String> getArtifactAllowedPathPrefixes() { return artifactAllowedPathPrefixes; }
    public void setArtifactAllowedPathPrefixes(List<String> prefixes) {
        this.artifactAllowedPathPrefixes = prefixes;
    }
    public Map<String, String> getTrustedSigningKeys() { return trustedSigningKeys; }
    public void setTrustedSigningKeys(Map<String, String> trustedSigningKeys) {
        this.trustedSigningKeys = trustedSigningKeys;
    }
    public int getMaxArtifactBytes() { return maxArtifactBytes; }
    public void setMaxArtifactBytes(int maxArtifactBytes) { this.maxArtifactBytes = maxArtifactBytes; }
    public int getMaxManifestBytes() { return maxManifestBytes; }
    public void setMaxManifestBytes(int maxManifestBytes) { this.maxManifestBytes = maxManifestBytes; }
    public int getMaxSchemaBytes() { return maxSchemaBytes; }
    public void setMaxSchemaBytes(int maxSchemaBytes) { this.maxSchemaBytes = maxSchemaBytes; }
    public boolean isLegacyWriteRetired() { return legacyWriteRetired; }
    public void setLegacyWriteRetired(boolean legacyWriteRetired) {
        this.legacyWriteRetired = legacyWriteRetired;
    }
}
