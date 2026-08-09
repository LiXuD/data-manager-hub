package com.dataplatform.common.plugin.runtime;

import com.dataplatform.common.plugin.artifact.IsolatedPluginClassLoader;
import com.dataplatform.common.plugin.artifact.PluginArtifactException;
import com.dataplatform.common.plugin.artifact.PluginManifest;
import com.dataplatform.common.plugin.artifact.VerifiedPluginArtifact;
import com.dataplatform.plugin.spi.ConnectorPlugin;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginSelfTestResult;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class PluginLoader {

    private final PluginContextFactory contextFactory;
    private final String hostVersion;
    private final String supportedSpiVersion;
    private final ClassLoader hostClassLoader;

    public PluginLoader(PluginContext context, String hostVersion, String supportedSpiVersion) {
        this(manifest -> Objects.requireNonNull(context, "context"), hostVersion, supportedSpiVersion);
    }

    public PluginLoader(PluginContextFactory contextFactory, String hostVersion, String supportedSpiVersion) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
        this.hostVersion = requireVersion(hostVersion, "hostVersion");
        this.supportedSpiVersion = requireVersion(supportedSpiVersion, "supportedSpiVersion");
        this.hostClassLoader = ConnectorPlugin.class.getClassLoader();
    }

    public PluginHandle load(VerifiedPluginArtifact artifact) {
        PluginManifest manifest = artifact.manifest();
        validateCompatibility(manifest);
        IsolatedPluginClassLoader classLoader = null;
        ConnectorPlugin plugin = null;
        try {
            classLoader = new IsolatedPluginClassLoader(artifact.jarPath().toUri().toURL(), hostClassLoader);
            Class<?> entryClass = Class.forName(manifest.entryClass(), true, classLoader);
            if (!ConnectorPlugin.class.isAssignableFrom(entryClass)) {
                throw new PluginArtifactException("Plugin entryClass does not implement ConnectorPlugin");
            }
            plugin = (ConnectorPlugin) entryClass.getDeclaredConstructor().newInstance();
            if (!plugin.descriptor().equals(manifest.descriptor())) {
                throw new PluginArtifactException("Plugin descriptor does not match Manifest");
            }
            ConnectorPlugin initializedPlugin = plugin;
            PluginContext context = Objects.requireNonNull(contextFactory.create(manifest),
                    "PluginContextFactory returned null");
            withContextClassLoader(classLoader, () -> {
                initializedPlugin.initialize(context);
                return null;
            });
            PluginSelfTestResult selfTest = selfTest(classLoader, initializedPlugin);
            if (selfTest == null || !selfTest.successful()) {
                throw new PluginArtifactException("Plugin self-test failed: "
                        + (selfTest == null ? "no result" : ConnectorSafeMessageSanitizer.sanitize(
                                selfTest.safeMessage(), java.util.List.of())));
            }
            return new PluginHandle(plugin, classLoader, classLoader);
        } catch (PluginArtifactException exception) {
            closeFailed(plugin, classLoader);
            throw exception;
        } catch (Exception | LinkageError exception) {
            closeFailed(plugin, classLoader);
            throw new PluginArtifactException("Plugin loading failed", exception);
        }
    }

    private PluginSelfTestResult selfTest(ClassLoader classLoader, ConnectorPlugin plugin) throws Exception {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var future = executor.submit(() -> withContextClassLoader(classLoader, plugin::selfTest));
            try {
                return future.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException exception) {
                future.cancel(true);
                throw new PluginArtifactException("Plugin self-test exceeded its deadline", exception);
            } catch (ExecutionException exception) {
                if (exception.getCause() instanceof Exception cause) throw cause;
                if (exception.getCause() instanceof Error cause) throw cause;
                throw exception;
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void validateCompatibility(PluginManifest manifest) {
        if (major(manifest.spiVersion()) != major(supportedSpiVersion)) {
            throw new PluginArtifactException("Plugin SPI major version is incompatible");
        }
        if (compareVersions(hostVersion, manifest.minHostVersion()) < 0) {
            throw new PluginArtifactException("Plugin requires a newer host version");
        }
    }

    private void closeFailed(ConnectorPlugin plugin, IsolatedPluginClassLoader classLoader) {
        if (plugin != null) {
            try { plugin.close(); } catch (Exception ignored) { }
        }
        if (classLoader != null) {
            try { classLoader.close(); } catch (Exception ignored) { }
        }
    }

    private <T> T withContextClassLoader(ClassLoader loader, PluginHandle.ThrowingSupplier<T> supplier)
            throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(loader);
            return supplier.get();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private int major(String version) {
        return Integer.parseInt(version.split("[.+-]", 2)[0]);
    }

    private int compareVersions(String left, String right) {
        int[] a = components(left);
        int[] b = components(right);
        for (int index = 0; index < 3; index++) {
            int comparison = Integer.compare(a[index], b[index]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private int[] components(String version) {
        String[] values = version.split("[-+]", 2)[0].split("\\.");
        return new int[]{Integer.parseInt(values[0]), Integer.parseInt(values[1]),
                values.length > 2 ? Integer.parseInt(values[2]) : 0};
    }

    private String requireVersion(String value, String field) {
        if (value == null || !value.matches("\\d+\\.\\d+(?:\\.\\d+)?(?:[-+][0-9A-Za-z.-]+)?")) {
            throw new IllegalArgumentException(field + " is not a supported semantic version");
        }
        return value;
    }
}
