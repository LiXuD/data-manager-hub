package com.dataplatform.common.plugin.artifact;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.lang.invoke.MethodHandles;
import org.junit.jupiter.api.Test;

class PluginBytecodePolicyTest {

    @Test
    void rejectsDirectNetworkThreadAndFilesystemEscapeApis() throws Exception {
        PluginArtifactException network = assertThrows(PluginArtifactException.class,
                () -> PluginBytecodePolicy.validate("DangerousNetwork", bytes(DangerousNetwork.class)));
        assertTrue(network.getMessage().contains("java/net/Socket"));

        PluginArtifactException thread = assertThrows(PluginArtifactException.class,
                () -> PluginBytecodePolicy.validate("DangerousThread", bytes(DangerousThread.class)));
        assertTrue(thread.getMessage().contains("java/util/concurrent/Executors"));

        PluginArtifactException file = assertThrows(PluginArtifactException.class,
                () -> PluginBytecodePolicy.validate("DangerousFile", bytes(DangerousFile.class)));
        assertTrue(file.getMessage().contains("java/nio/file/"));

        assertTrue(assertThrows(PluginArtifactException.class,
                () -> PluginBytecodePolicy.validate("DangerousNative", bytes(DangerousNative.class)))
                .getMessage().contains("java/lang/System"));
        assertTrue(assertThrows(PluginArtifactException.class,
                () -> PluginBytecodePolicy.validate("DangerousMethodHandles", bytes(DangerousMethodHandles.class)))
                .getMessage().contains("java/lang/invoke/MethodHandles"));
        assertTrue(assertThrows(PluginArtifactException.class,
                () -> PluginBytecodePolicy.validate("DangerousProcess", bytes(DangerousProcess.class)))
                .getMessage().contains("java/lang/ProcessBuilder"));
        assertTrue(assertThrows(PluginArtifactException.class,
                () -> PluginBytecodePolicy.validate("DangerousRawHttp", bytes(DangerousRawHttp.class)))
                .getMessage().contains("java/net/http/HttpClient"));
    }

    @Test
    void acceptsAPluginClassThatUsesOnlySpiSafeJdkTypes() throws Exception {
        assertDoesNotThrow(() -> PluginBytecodePolicy.validate("LegalPlugin", bytes(LegalPlugin.class)));
        assertDoesNotThrow(() -> PluginBytecodePolicy.validate(
                "LegalClassLiteralPlugin", bytes(LegalClassLiteralPlugin.class)));
    }

    @Test
    void permitsClassLiteralsRequiredByObjectCodecButRejectsClassReflectionMembers() throws Exception {
        PluginArtifactException reflection = assertThrows(PluginArtifactException.class,
                () -> PluginBytecodePolicy.validate("DangerousReflection", bytes(DangerousReflection.class)));
        assertTrue(reflection.getMessage().contains("java/lang/Class.forName"));
    }

    private byte[] bytes(Class<?> type) throws Exception {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException(resource);
            return input.readAllBytes();
        }
    }

    private static final class DangerousNetwork {
        private Socket socket;
    }

    private static final class DangerousThread {
        void start() { Executors.newSingleThreadExecutor(); }
    }

    private static final class DangerousFile {
        Object read() throws Exception { return java.nio.file.Files.readString(java.nio.file.Path.of("x")); }
    }

    private static final class LegalPlugin {
        String normalize(String value) { return value == null ? "" : value.trim(); }
    }

    private static final class LegalClassLiteralPlugin {
        Class<?> payloadType() { return Object.class; }
    }

    private static final class DangerousReflection {
        Class<?> resolve(String name) throws Exception { return Class.forName(name); }
    }

    private static final class DangerousNative {
        void load(String path) { System.load(path); }
    }

    private static final class DangerousMethodHandles {
        Object lookup() { return MethodHandles.lookup(); }
    }

    private static final class DangerousProcess {
        Process start() throws Exception { return new ProcessBuilder("echo").start(); }
    }

    private static final class DangerousRawHttp {
        Object client() { return java.net.http.HttpClient.newHttpClient(); }
    }
}
