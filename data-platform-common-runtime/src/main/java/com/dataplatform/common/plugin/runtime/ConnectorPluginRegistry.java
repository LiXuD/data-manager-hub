package com.dataplatform.common.plugin.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectorPluginRegistry implements AutoCloseable {

    private final ConcurrentHashMap<PluginKey, PluginHandle> versions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PluginKey> active = new ConcurrentHashMap<>();

    public void register(PluginHandle handle) {
        Objects.requireNonNull(handle, "handle");
        PluginHandle previous = versions.putIfAbsent(handle.key(), handle);
        if (previous != null) {
            handle.retire();
            throw new IllegalStateException("Plugin version is already registered: " + handle.key());
        }
    }

    public void activate(String pluginId, String version) {
        PluginKey nextKey = new PluginKey(pluginId, version);
        PluginHandle next = requireHandle(nextKey);
        if (next.state() == PluginHandleState.RETIRED || next.state() == PluginHandleState.CLOSED) {
            throw new IllegalStateException("Plugin version cannot be activated: " + nextKey);
        }
        active.compute(pluginId, (ignored, previousKey) -> {
            if (previousKey != null && !previousKey.equals(nextKey)) {
                PluginHandle previous = versions.get(previousKey);
                if (previous != null) {
                    previous.markInactive();
                }
            }
            next.markActive();
            return nextKey;
        });
    }

    public PluginHandle.Lease acquire(String pluginId, String version) {
        return requireHandle(new PluginKey(pluginId, version)).acquire();
    }

    public PluginHandle.Lease acquireActive(String pluginId) {
        PluginKey key = active.get(pluginId);
        if (key == null) {
            throw new IllegalStateException("Plugin has no active version: " + pluginId);
        }
        return requireHandle(key).acquire();
    }

    public Optional<PluginHandleState> state(String pluginId, String version) {
        return Optional.ofNullable(versions.get(new PluginKey(pluginId, version))).map(PluginHandle::state);
    }

    public boolean isLoaded(String pluginId, String version) {
        PluginHandle handle = versions.get(new PluginKey(pluginId, version));
        return handle != null && handle.state() != PluginHandleState.CLOSED;
    }

    public boolean release(String pluginId, String version) {
        PluginKey key = new PluginKey(pluginId, version);
        PluginHandle handle = versions.get(key);
        if (handle == null) {
            return true;
        }
        if (key.equals(active.get(pluginId)) || handle.referenceCount() > 0) {
            return false;
        }
        if (versions.remove(key, handle)) {
            handle.retire();
        }
        return true;
    }

    public Map<PluginKey, PluginHandleState> states() {
        return versions.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> entry.getValue().state()));
    }

    public void retire(String pluginId, String version) {
        PluginKey key = new PluginKey(pluginId, version);
        if (key.equals(active.get(pluginId))) {
            throw new IllegalStateException("Active plugin version cannot be retired: " + key);
        }
        PluginHandle handle = versions.remove(key);
        if (handle != null) {
            handle.retire();
        }
    }

    private PluginHandle requireHandle(PluginKey key) {
        PluginHandle handle = versions.get(key);
        if (handle == null) {
            throw new IllegalStateException("Plugin version is not registered: " + key);
        }
        return handle;
    }

    @Override
    public void close() {
        active.clear();
        versions.values().forEach(PluginHandle::retire);
        versions.clear();
    }
}
