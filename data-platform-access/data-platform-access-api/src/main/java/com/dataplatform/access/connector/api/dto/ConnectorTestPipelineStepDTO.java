package com.dataplatform.access.connector.api.dto;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable draft step passed to Access for a controlled connector test. */
public class ConnectorTestPipelineStepDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String stageKey;
    private String capability;
    private String pluginId;
    private String pluginVersion;
    private Integer order;
    private Boolean enabled;
    private Map<String, Object> config = new LinkedHashMap<>();
    private String configHash;
    private String artifactSha256;
    private String manifestHash;
    private String schemaHash;

    public String getStageKey() { return stageKey; }
    public void setStageKey(String stageKey) { this.stageKey = stageKey; }
    public String getCapability() { return capability; }
    public void setCapability(String capability) { this.capability = capability; }
    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }
    public String getPluginVersion() { return pluginVersion; }
    public void setPluginVersion(String pluginVersion) { this.pluginVersion = pluginVersion; }
    public Integer getOrder() { return order; }
    public void setOrder(Integer order) { this.order = order; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) {
        this.config = config != null ? config : new LinkedHashMap<>();
    }
    public String getConfigHash() { return configHash; }
    public void setConfigHash(String configHash) { this.configHash = configHash; }
    public String getArtifactSha256() { return artifactSha256; }
    public void setArtifactSha256(String artifactSha256) { this.artifactSha256 = artifactSha256; }
    public String getManifestHash() { return manifestHash; }
    public void setManifestHash(String manifestHash) { this.manifestHash = manifestHash; }
    public String getSchemaHash() { return schemaHash; }
    public void setSchemaHash(String schemaHash) { this.schemaHash = schemaHash; }
}
