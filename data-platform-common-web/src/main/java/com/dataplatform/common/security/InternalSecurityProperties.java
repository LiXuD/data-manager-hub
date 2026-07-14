package com.dataplatform.common.security;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform.security.internal")
public class InternalSecurityProperties {

    private boolean enabled;
    private String issuer = "data-platform-identity";
    private String serviceName;
    private String tokenUri;
    private String clientSecret;
    private String publicKeyLocation;
    private String privateKeyLocation;
    private Duration tokenTtl = Duration.ofMinutes(5);
    private Duration tokenConnectTimeout = Duration.ofSeconds(2);
    private Duration tokenReadTimeout = Duration.ofSeconds(5);
    private Duration tokenRetryBackoff = Duration.ofMillis(200);
    private int tokenMaxAttempts = 3;
    private Map<String, ClientRegistration> clients = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getTokenUri() { return tokenUri; }
    public void setTokenUri(String tokenUri) { this.tokenUri = tokenUri; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getPublicKeyLocation() { return publicKeyLocation; }
    public void setPublicKeyLocation(String publicKeyLocation) { this.publicKeyLocation = publicKeyLocation; }
    public String getPrivateKeyLocation() { return privateKeyLocation; }
    public void setPrivateKeyLocation(String privateKeyLocation) { this.privateKeyLocation = privateKeyLocation; }
    public Duration getTokenTtl() { return tokenTtl; }
    public void setTokenTtl(Duration tokenTtl) { this.tokenTtl = tokenTtl; }
    public Duration getTokenConnectTimeout() { return tokenConnectTimeout; }
    public void setTokenConnectTimeout(Duration tokenConnectTimeout) { this.tokenConnectTimeout = tokenConnectTimeout; }
    public Duration getTokenReadTimeout() { return tokenReadTimeout; }
    public void setTokenReadTimeout(Duration tokenReadTimeout) { this.tokenReadTimeout = tokenReadTimeout; }
    public Duration getTokenRetryBackoff() { return tokenRetryBackoff; }
    public void setTokenRetryBackoff(Duration tokenRetryBackoff) { this.tokenRetryBackoff = tokenRetryBackoff; }
    public int getTokenMaxAttempts() { return tokenMaxAttempts; }
    public void setTokenMaxAttempts(int tokenMaxAttempts) { this.tokenMaxAttempts = tokenMaxAttempts; }
    public Map<String, ClientRegistration> getClients() { return clients; }
    public void setClients(Map<String, ClientRegistration> clients) { this.clients = clients; }

    public static class ClientRegistration {
        private String secret;
        private Map<String, Set<String>> grants = new LinkedHashMap<>();

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public Map<String, Set<String>> getGrants() { return grants; }
        public void setGrants(Map<String, Set<String>> grants) { this.grants = grants; }
    }
}
