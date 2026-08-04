package com.dataplatform.common.plugin.artifact;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * Version-scoped child-first loader. This prevents dependency collisions but is
 * deliberately not a malicious-code sandbox.
 */
public final class IsolatedPluginClassLoader extends URLClassLoader {

    private static final List<String> PARENT_FIRST = List.of(
            "java.", "javax.", "jakarta.", "org.slf4j.",
            "com.fasterxml.jackson.", "com.dataplatform.plugin.spi.");
    private static final List<String> FORBIDDEN_HOST = List.of(
            "com.dataplatform.common.", "com.dataplatform.access.",
            "com.dataplatform.masterdata.", "com.dataplatform.billing.",
            "com.dataplatform.identity.", "com.dataplatform.governance.");

    static {
        registerAsParallelCapable();
    }

    public IsolatedPluginClassLoader(URL artifact, ClassLoader parent) {
        super(new URL[]{artifact}, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (FORBIDDEN_HOST.stream().anyMatch(name::startsWith)) {
                    throw new ClassNotFoundException("Plugin access to host implementation is forbidden: " + name);
                }
                if (PARENT_FIRST.stream().anyMatch(name::startsWith)) {
                    loaded = super.loadClass(name, false);
                } else {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loaded = super.loadClass(name, false);
                    }
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }
}
