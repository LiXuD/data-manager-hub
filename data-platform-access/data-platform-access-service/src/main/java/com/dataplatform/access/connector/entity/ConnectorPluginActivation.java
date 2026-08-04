package com.dataplatform.access.connector.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** Access-owned persistence fact for one plugin version on one service instance. */
@TableName("connector_plugin_activation")
public class ConnectorPluginActivation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String serviceInstanceId;
    private String pluginId;
    private String pluginVersion;
    private String artifactSha256;
    private String hostVersion;
    private String state;
    private LocalDateTime loadedAt;
    private LocalDateTime lastHeartbeatAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String safeErrorCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String safeErrorDigest;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
