package com.dataplatform.common.plugin.runtime;

import com.dataplatform.common.plugin.artifact.PluginArtifactCoordinates;
import com.dataplatform.common.plugin.artifact.PluginArtifactVerifier;
import com.dataplatform.common.plugin.artifact.VerifiedPluginArtifact;
import java.util.Objects;

/**
 * Spring-usable façade for Access-owned artifact caching and activation orchestration.
 * Downloading remains an Access responsibility; this façade accepts a verified local path.
 */
public final class PluginRuntimeManager implements AutoCloseable {

    private final PluginArtifactVerifier verifier;
    private final PluginLoader loader;
    private final ConnectorPluginRegistry registry;

    public PluginRuntimeManager(PluginArtifactVerifier verifier, PluginLoader loader,
                                ConnectorPluginRegistry registry) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public PluginKey preload(PluginArtifactCoordinates coordinates) {
        VerifiedPluginArtifact artifact = verifier.verify(coordinates);
        PluginHandle handle = loader.load(artifact);
        registry.register(handle);
        return handle.key();
    }

    public void registerBuiltIn(ConnectorPluginRegistration registration) {
        registry.register(PluginHandle.builtIn(registration.plugin()));
    }

    public boolean isLoaded(String pluginId, String version) {
        return registry.isLoaded(pluginId, version);
    }

    public void activate(String pluginId, String version) {
        registry.activate(pluginId, version);
    }

    /** Returns false when the version is active or pinned by a compiled pipeline. */
    public boolean release(String pluginId, String version) {
        return registry.release(pluginId, version);
    }

    public ConnectorPluginRegistry registry() {
        return registry;
    }

    public int isolatedClassLoaderCount() {
        return PluginHandle.isolatedClassLoaderCount();
    }

    @Override
    public void close() {
        registry.close();
    }

    public record ConnectorPluginRegistration(com.dataplatform.plugin.spi.ConnectorPlugin plugin) {
        public ConnectorPluginRegistration {
            Objects.requireNonNull(plugin, "plugin");
        }
    }
}
