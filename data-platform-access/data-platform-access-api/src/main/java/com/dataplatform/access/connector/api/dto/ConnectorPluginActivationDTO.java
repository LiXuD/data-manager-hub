package com.dataplatform.access.connector.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Runtime loading fact reported by one Access service instance. */
public class ConnectorPluginActivationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String serviceInstanceId;
    private String pluginId;
    private String pluginVersion;
    private String artifactSha256;
    private String hostVersion;
    private String state;
    private LocalDateTime loadedAt;
    private LocalDateTime lastHeartbeatAt;
    private String safeErrorCode;
    private String safeErrorDigest;

    public String getServiceInstanceId() { return serviceInstanceId; }
    public void setServiceInstanceId(String serviceInstanceId) { this.serviceInstanceId = serviceInstanceId; }
    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }
    public String getPluginVersion() { return pluginVersion; }
    public void setPluginVersion(String pluginVersion) { this.pluginVersion = pluginVersion; }
    public String getArtifactSha256() { return artifactSha256; }
    public void setArtifactSha256(String artifactSha256) { this.artifactSha256 = artifactSha256; }
    public String getHostVersion() { return hostVersion; }
    public void setHostVersion(String hostVersion) { this.hostVersion = hostVersion; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public LocalDateTime getLoadedAt() { return loadedAt; }
    public void setLoadedAt(LocalDateTime loadedAt) { this.loadedAt = loadedAt; }
    public LocalDateTime getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(LocalDateTime lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
    public String getSafeErrorCode() { return safeErrorCode; }
    public void setSafeErrorCode(String safeErrorCode) { this.safeErrorCode = safeErrorCode; }
    public String getSafeErrorDigest() { return safeErrorDigest; }
    public void setSafeErrorDigest(String safeErrorDigest) { this.safeErrorDigest = safeErrorDigest; }
}
