package com.dataplatform.access.connector.runtime;

import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import com.dataplatform.common.plugin.artifact.PluginArtifactException;
import com.dataplatform.common.plugin.artifact.PluginManifest;
import com.dataplatform.common.plugin.artifact.PluginPermissions;
import com.dataplatform.common.plugin.runtime.DefaultPluginContext;
import com.dataplatform.common.plugin.runtime.JacksonObjectCodec;
import com.dataplatform.common.plugin.runtime.PluginContextFactory;
import com.dataplatform.common.plugin.transport.NetworkPolicy;
import com.dataplatform.common.plugin.transport.OkHttpManagedTransport;
import com.dataplatform.plugin.spi.ManagedTaskExecutor;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginLogger;
import com.dataplatform.plugin.spi.PluginMetricRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import okhttp3.OkHttpClient;

/** Creates a least-privilege context by intersecting host policy with the signed Manifest. */
public final class ManifestScopedPluginContextFactory implements PluginContextFactory {

    private final ConnectorRuntimeProperties properties;
    private final OkHttpClient httpClient;
    private final ScopedConnectorSecretResolver secretResolver;
    private final Clock clock;
    private final PluginLogger logger;
    private final PluginMetricRecorder metrics;
    private final ObjectMapper objectMapper;
    private final ManagedTaskExecutor taskExecutor;

    public ManifestScopedPluginContextFactory(
            ConnectorRuntimeProperties properties,
            OkHttpClient httpClient,
            ScopedConnectorSecretResolver secretResolver,
            Clock clock,
            PluginLogger logger,
            PluginMetricRecorder metrics,
            ObjectMapper objectMapper,
            ManagedTaskExecutor taskExecutor) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.secretResolver = secretResolver;
        this.clock = clock;
        this.logger = logger;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public PluginContext create(PluginManifest manifest) {
        PluginPermissions permissions = manifest.permissions();
        if (permissions == null) {
            throw new PluginArtifactException("Plugin network permissions are required");
        }
        boolean noNetworkRequested = permissions.networkProtocols().isEmpty()
                && permissions.networkHosts().isEmpty();
        if (permissions.networkProtocols().isEmpty() != permissions.networkHosts().isEmpty()) {
            throw new PluginArtifactException("Plugin network protocol and host permissions must be declared together");
        }
        Set<String> protocols = noNetworkRequested ? Set.of("https") : intersection(
                properties.getNetworkAllowedProtocols(), permissions.networkProtocols());
        Set<String> hosts = noNetworkRequested ? Set.of("disabled.invalid") : intersectHosts(
                properties.getNetworkAllowedHosts(), permissions.networkHosts());
        if (protocols.isEmpty() || hosts.isEmpty()) {
            throw new PluginArtifactException("Plugin network permissions are outside the host allowlist");
        }
        NetworkPolicy policy = new NetworkPolicy(protocols, hosts, properties.isAllowPrivateNetworks(),
                Duration.ofMillis(properties.getMaxConnectTimeoutMs()),
                Duration.ofMillis(properties.getMaxReadTimeoutMs()),
                Duration.ofMillis(properties.getMaxTotalTimeoutMs()),
                properties.getMaxResponseBytes());
        return new DefaultPluginContext(new OkHttpManagedTransport(httpClient, policy), secretResolver,
                clock, logger, metrics, new JacksonObjectCodec(objectMapper), taskExecutor);
    }

    static Set<String> intersection(Iterable<String> platform, Iterable<String> manifest) {
        Set<String> allowed = normalized(platform);
        Set<String> result = new LinkedHashSet<>();
        for (String value : manifest) {
            String normalized = normalize(value);
            if (allowed.contains(normalized)) {
                result.add(normalized);
            }
        }
        return Set.copyOf(result);
    }

    static Set<String> intersectHosts(Iterable<String> platform, Iterable<String> manifest) {
        Set<String> platformHosts = normalized(platform);
        Set<String> manifestHosts = normalized(manifest);
        Set<String> result = new LinkedHashSet<>();
        for (String platformHost : platformHosts) {
            for (String manifestHost : manifestHosts) {
                String narrower = narrowerHostPattern(platformHost, manifestHost);
                if (narrower != null) {
                    result.add(narrower);
                }
            }
        }
        return Set.copyOf(result);
    }

    private static String narrowerHostPattern(String left, String right) {
        if (left.equals(right)) return left;
        if (matches(left, right) && !right.startsWith("*.")) return right;
        if (matches(right, left) && !left.startsWith("*.")) return left;
        if (left.startsWith("*.") && right.startsWith("*.")) {
            if (left.substring(1).endsWith(right.substring(1))) return left;
            if (right.substring(1).endsWith(left.substring(1))) return right;
        }
        return null;
    }

    private static boolean matches(String pattern, String host) {
        return pattern.equals(host) || (pattern.startsWith("*.")
                && host.length() > pattern.length() - 1
                && host.endsWith(pattern.substring(1)));
    }

    private static Set<String> normalized(Iterable<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String normalized = normalize(value);
                if (!normalized.isEmpty()) result.add(normalized);
            }
        }
        return result;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
