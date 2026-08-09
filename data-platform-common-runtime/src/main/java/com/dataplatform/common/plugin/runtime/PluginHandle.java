package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.ConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorStageFactory;
import com.dataplatform.plugin.spi.StageCapability;
import java.io.Closeable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class PluginHandle {
    private static final AtomicInteger ISOLATED_CLASSLOADERS = new AtomicInteger();

    private final PluginKey key;
    private final ConnectorPlugin plugin;
    private final ClassLoader classLoader;
    private final Closeable closeableClassLoader;
    private final Map<StageCapability, ConnectorStageFactory> factories;
    private final AtomicInteger references = new AtomicInteger();
    private final AtomicReference<PluginHandleState> state = new AtomicReference<>(PluginHandleState.READY);

    public PluginHandle(ConnectorPlugin plugin, ClassLoader classLoader, Closeable closeableClassLoader) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.key = new PluginKey(plugin.descriptor().pluginId(), plugin.descriptor().version());
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.closeableClassLoader = closeableClassLoader;
        EnumMap<StageCapability, ConnectorStageFactory> discovered = new EnumMap<>(StageCapability.class);
        for (ConnectorStageFactory factory : plugin.stageFactories()) {
            if (factory == null || discovered.putIfAbsent(factory.capability(), factory) != null) {
                throw new IllegalArgumentException("Plugin defines a null or duplicate stage capability");
            }
        }
        if (!discovered.keySet().equals(plugin.descriptor().capabilities())) {
            throw new IllegalArgumentException("Plugin descriptor capabilities do not match stage factories");
        }
        this.factories = Map.copyOf(discovered);
        if (closeableClassLoader != null) ISOLATED_CLASSLOADERS.incrementAndGet();
    }

    public static PluginHandle builtIn(ConnectorPlugin plugin) {
        return new PluginHandle(plugin, plugin.getClass().getClassLoader(), null);
    }

    public PluginKey key() { return key; }
    public ConnectorPlugin plugin() { return plugin; }
    public ClassLoader classLoader() { return classLoader; }
    public PluginHandleState state() { return state.get(); }
    public int referenceCount() { return references.get(); }
    public static int isolatedClassLoaderCount() { return ISOLATED_CLASSLOADERS.get(); }

    public ConnectorStageFactory factory(StageCapability capability) {
        ConnectorStageFactory factory = factories.get(capability);
        if (factory == null) {
            throw new IllegalArgumentException("Plugin does not provide capability " + capability);
        }
        return factory;
    }

    public Lease acquire() {
        while (true) {
            PluginHandleState current = state.get();
            if (current == PluginHandleState.RETIRED || current == PluginHandleState.CLOSED) {
                throw new IllegalStateException("Plugin version is not available: " + key);
            }
            int count = references.get();
            if (references.compareAndSet(count, count + 1)) {
                if (state.get() == PluginHandleState.RETIRED || state.get() == PluginHandleState.CLOSED) {
                    release();
                    throw new IllegalStateException("Plugin version retired during acquisition: " + key);
                }
                return new Lease(this);
            }
        }
    }

    void markActive() {
        state.compareAndSet(PluginHandleState.READY, PluginHandleState.ACTIVE);
    }

    void markInactive() {
        state.compareAndSet(PluginHandleState.ACTIVE, PluginHandleState.READY);
    }

    public void retire() {
        PluginHandleState previous = state.getAndUpdate(current ->
                current == PluginHandleState.CLOSED ? current : PluginHandleState.RETIRED);
        if (previous != PluginHandleState.CLOSED && references.get() == 0) {
            closeResources();
        }
    }

    private void release() {
        int remaining = references.decrementAndGet();
        if (remaining < 0) {
            references.incrementAndGet();
            throw new IllegalStateException("Plugin reference count underflow: " + key);
        }
        if (remaining == 0 && state.get() == PluginHandleState.RETIRED) {
            closeResources();
        }
    }

    private synchronized void closeResources() {
        if (state.get() == PluginHandleState.CLOSED) {
            return;
        }
        try {
            withContextClassLoader(() -> {
                plugin.close();
                return null;
            });
        } catch (Exception ignored) {
            // Lifecycle failure is observable by the host registry, but must not block unloading.
        } finally {
            try {
                if (closeableClassLoader != null) {
                    try {
                        closeableClassLoader.close();
                    } finally {
                        ISOLATED_CLASSLOADERS.decrementAndGet();
                    }
                }
            } catch (Exception ignored) {
                // Best effort: the version is still removed from the active registry.
            }
            state.set(PluginHandleState.CLOSED);
        }
    }

    public <T> T withContextClassLoader(ThrowingSupplier<T> action) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(classLoader);
            return action.get();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public static final class Lease implements AutoCloseable {
        private final PluginHandle handle;
        private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();

        private Lease(PluginHandle handle) {
            this.handle = handle;
        }

        public PluginHandle handle() { return handle; }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                handle.release();
            }
        }
    }
}
