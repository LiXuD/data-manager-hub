package com.dataplatform.access.connector.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connector.runtime")
public class ConnectorRuntimeProperties {

    private String instanceId;
    private String hostVersion = "1.0.0-SNAPSHOT";
    private long heartbeatIntervalMs = 30_000L;
    private long activationPollIntervalMs = 2_000L;
    private String cacheDirectory = System.getProperty("java.io.tmpdir") + "/data-platform/plugins";
    private List<String> repositoryAllowedPrefixes = new ArrayList<>();
    private List<String> networkAllowedProtocols = new ArrayList<>(List.of("https"));
    private List<String> networkAllowedHosts = new ArrayList<>();
    private boolean allowPrivateNetworks;
    private long maxConnectTimeoutMs = 5_000L;
    private long maxReadTimeoutMs = 30_000L;
    private long maxTotalTimeoutMs = 60_000L;
    private long testTimeoutMs = 30_000L;
    private long maxResponseBytes = 10L * 1024L * 1024L;
    private Map<String, SigningKey> signingKeys = new LinkedHashMap<>();

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getHostVersion() { return hostVersion; }
    public void setHostVersion(String hostVersion) { this.hostVersion = hostVersion; }
    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }
    public long getActivationPollIntervalMs() { return activationPollIntervalMs; }
    public void setActivationPollIntervalMs(long activationPollIntervalMs) {
        this.activationPollIntervalMs = activationPollIntervalMs;
    }
    public String getCacheDirectory() { return cacheDirectory; }
    public void setCacheDirectory(String cacheDirectory) { this.cacheDirectory = cacheDirectory; }
    public List<String> getRepositoryAllowedPrefixes() { return repositoryAllowedPrefixes; }
    public void setRepositoryAllowedPrefixes(List<String> repositoryAllowedPrefixes) {
        this.repositoryAllowedPrefixes = repositoryAllowedPrefixes != null
                ? repositoryAllowedPrefixes : new ArrayList<>();
    }
    public List<String> getNetworkAllowedProtocols() { return networkAllowedProtocols; }
    public void setNetworkAllowedProtocols(List<String> networkAllowedProtocols) {
        this.networkAllowedProtocols = networkAllowedProtocols != null
                ? networkAllowedProtocols : new ArrayList<>();
    }
    public List<String> getNetworkAllowedHosts() { return networkAllowedHosts; }
    public void setNetworkAllowedHosts(List<String> networkAllowedHosts) {
        this.networkAllowedHosts = networkAllowedHosts != null ? networkAllowedHosts : new ArrayList<>();
    }
    public boolean isAllowPrivateNetworks() { return allowPrivateNetworks; }
    public void setAllowPrivateNetworks(boolean allowPrivateNetworks) { this.allowPrivateNetworks = allowPrivateNetworks; }
    public long getMaxConnectTimeoutMs() { return maxConnectTimeoutMs; }
    public void setMaxConnectTimeoutMs(long maxConnectTimeoutMs) { this.maxConnectTimeoutMs = maxConnectTimeoutMs; }
    public long getMaxReadTimeoutMs() { return maxReadTimeoutMs; }
    public void setMaxReadTimeoutMs(long maxReadTimeoutMs) { this.maxReadTimeoutMs = maxReadTimeoutMs; }
    public long getMaxTotalTimeoutMs() { return maxTotalTimeoutMs; }
    public void setMaxTotalTimeoutMs(long maxTotalTimeoutMs) { this.maxTotalTimeoutMs = maxTotalTimeoutMs; }
    public long getTestTimeoutMs() { return testTimeoutMs; }
    public void setTestTimeoutMs(long testTimeoutMs) { this.testTimeoutMs = testTimeoutMs; }
    public long getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(long maxResponseBytes) { this.maxResponseBytes = maxResponseBytes; }
    public Map<String, SigningKey> getSigningKeys() { return signingKeys; }
    public void setSigningKeys(Map<String, SigningKey> signingKeys) {
        this.signingKeys = signingKeys != null ? signingKeys : new LinkedHashMap<>();
    }

    public static class SigningKey {
        private String resource;
        private String algorithm = "Ed25519";

        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    }
}
