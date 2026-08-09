package com.dataplatform.common.plugin.runtime;

import com.dataplatform.common.plugin.TestPluginContexts;
import com.dataplatform.common.plugin.artifact.PluginArtifactCoordinates;
import com.dataplatform.common.plugin.artifact.PluginArtifactVerifier;
import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.dataplatform.common.plugin.artifact.TrustedSigningKey;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.CompiledStageConfig;
import com.dataplatform.plugin.spi.ConnectorExchange;
import com.dataplatform.plugin.spi.ConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorStage;
import com.dataplatform.plugin.spi.ConnectorStageFactory;
import com.dataplatform.plugin.spi.PluginContext;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.PluginSelfTestResult;
import com.dataplatform.plugin.spi.PluginValidationContext;
import com.dataplatform.plugin.spi.StageExecutionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import example.plugin.Stage3FixturePluginV1;
import example.plugin.Stage3FixturePluginV2;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real signed-JAR lifecycle acceptance for the release gates that ordinary unit mocks cannot prove.
 */
class PluginRuntimeStage3AcceptanceTest {

    private static final long MAX_P95_NANOS = TimeUnit.MILLISECONDS.toNanos(5);
    private static final long MAX_RETAINED_METASPACE_GROWTH = 8L * 1024L * 1024L;

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void switchesVersionsWithoutInterruptingPinnedPipeline() throws Exception {
        FixtureArtifacts artifacts = createArtifacts();
        ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
        try (PluginRuntimeManager manager = manager(artifacts, registry)) {
            manager.preload(artifacts.v1());
            manager.activate("stage3-fixture", "1.0.0");
            CompiledConnectorPipeline pinned = compile(registry, "1.0.0", "pipeline-v1");

            manager.preload(artifacts.v2());
            manager.activate("stage3-fixture", "2.0.0");
            assertEquals(PluginHandleState.READY, registry.state("stage3-fixture", "1.0.0").orElseThrow());
            assertEquals(PluginHandleState.ACTIVE, registry.state("stage3-fixture", "2.0.0").orElseThrow());
            assertFalse(manager.release("stage3-fixture", "1.0.0"),
                    "an in-flight compiled pipeline must pin its original version");

            var result = executor().execute(pinned, request());
            assertEquals(Map.of("accepted", true), result.normalizedData());
            assertEquals("pipeline-v1", result.pipelineVersion());

            pinned.close();
            assertTrue(manager.release("stage3-fixture", "1.0.0"));
            assertFalse(manager.isLoaded("stage3-fixture", "1.0.0"));
            assertTrue(manager.isLoaded("stage3-fixture", "2.0.0"));
        }
    }

    @Test
    void loadsAndUnloadsOneHundredTimesWithoutRegistryOrMetaspaceLeak() throws Exception {
        FixtureArtifacts artifacts = createArtifacts();
        ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
        List<WeakReference<ClassLoader>> unloaded = new ArrayList<>();
        try (PluginRuntimeManager manager = manager(artifacts, registry)) {
            for (int cycle = 0; cycle < 10; cycle++) {
                loadAndRelease(manager, registry, artifacts.v1(), unloaded);
            }
            forceClassUnloading(unloaded);
            long baselineMetaspace = metaspaceUsed();

            for (int cycle = 0; cycle < 100; cycle++) {
                loadAndRelease(manager, registry, artifacts.v1(), unloaded);
            }
            forceClassUnloading(unloaded);
            long retainedGrowth = metaspaceUsed() - baselineMetaspace;
            long aliveClassLoaders = unloaded.stream().filter(reference -> reference.get() != null).count();

            System.out.printf("stage3 lifecycle: cycles=100, aliveClassLoaders=%d, retainedMetaspaceGrowthBytes=%d%n",
                    aliveClassLoaders, retainedGrowth);

            assertTrue(registry.states().isEmpty(), "no released plugin version may remain registered");
            assertTrue(aliveClassLoaders <= 2,
                    () -> "isolated classloaders still strongly reachable after unloading: " + aliveClassLoaders);
            assertTrue(retainedGrowth <= MAX_RETAINED_METASPACE_GROWTH,
                    () -> "retained Metaspace grew by " + retainedGrowth + " bytes");
        }
    }

    @Test
    void loadedPipelineOrchestrationP95StaysWithinFiveMilliseconds() throws Exception {
        FixtureArtifacts artifacts = createArtifacts();
        ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
        try (PluginRuntimeManager manager = manager(artifacts, registry)) {
            manager.preload(artifacts.v1());
            try (CompiledConnectorPipeline pipeline = compile(registry, "1.0.0", "performance-v1")) {
                ConnectorPipelineExecutor executor = executor();
                for (int warmup = 0; warmup < 2_000; warmup++) {
                    assertTrue(executor.execute(pipeline, request()).successful());
                }
                long[] durations = new long[10_000];
                for (int index = 0; index < durations.length; index++) {
                    long started = System.nanoTime();
                    var result = executor.execute(pipeline, request());
                    durations[index] = System.nanoTime() - started;
                    assertTrue(result.successful());
                }
                java.util.Arrays.sort(durations);
                long p95 = durations[(int) Math.ceil(durations.length * 0.95) - 1];
                System.out.printf("stage3 pipeline: samples=%d, p95Nanos=%d, limitNanos=%d%n",
                        durations.length, p95, MAX_P95_NANOS);
                assertTrue(p95 <= MAX_P95_NANOS,
                        () -> "pipeline orchestration P95 was " + p95 + "ns, limit is " + MAX_P95_NANOS + "ns");
            }
        }
    }

    @Test
    void isolatesDifferentImplementationsOfTheSameDependencyClass() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        PluginArtifactCoordinates v1 = dynamicallyCompiledArtifact(pair, "1.0.0", "dependency-v1");
        PluginArtifactCoordinates v2 = dynamicallyCompiledArtifact(pair, "2.0.0", "dependency-v2");
        ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
        FixtureArtifacts artifacts = new FixtureArtifacts(pair, v1, v2);
        try (PluginRuntimeManager manager = manager(artifacts, registry)) {
            manager.preload(v1);
            manager.preload(v2);
            try (PluginHandle.Lease first = registry.acquire("stage3-dependency-fixture", "1.0.0");
                 PluginHandle.Lease second = registry.acquire("stage3-dependency-fixture", "2.0.0")) {
                assertEquals("dependency-v1", first.handle().plugin().descriptor().displayName());
                assertEquals("dependency-v2", second.handle().plugin().descriptor().displayName());
                assertNotSame(first.handle().classLoader(), second.handle().classLoader());
                Class<?> firstDependency = first.handle().classLoader().loadClass("fixture.shared.VersionedDependency");
                Class<?> secondDependency = second.handle().classLoader().loadClass("fixture.shared.VersionedDependency");
                assertNotSame(firstDependency, secondDependency);
                assertEquals("dependency-v1", firstDependency.getMethod("value")
                        .invoke(firstDependency.getDeclaredConstructor().newInstance()));
                assertEquals("dependency-v2", secondDependency.getMethod("value")
                        .invoke(secondDependency.getDeclaredConstructor().newInstance()));
            }
        }
    }

    @Test
    void closeFailureCannotBlockSwitchToTheNextVersion() {
        ConnectorPluginRegistry registry = new ConnectorPluginRegistry();
        registry.register(PluginHandle.builtIn(new ThrowingClosePlugin("1.0.0", true)));
        registry.register(PluginHandle.builtIn(new ThrowingClosePlugin("2.0.0", false)));
        registry.activate("throwing-close-fixture", "1.0.0");
        registry.activate("throwing-close-fixture", "2.0.0");

        assertTrue(registry.release("throwing-close-fixture", "1.0.0"));
        assertEquals(PluginHandleState.ACTIVE,
                registry.state("throwing-close-fixture", "2.0.0").orElseThrow());
        try (PluginHandle.Lease ignored = registry.acquireActive("throwing-close-fixture")) {
            assertEquals("2.0.0", ignored.handle().key().version());
        }
        registry.close();
    }

    private void loadAndRelease(PluginRuntimeManager manager, ConnectorPluginRegistry registry,
                                PluginArtifactCoordinates coordinates,
                                List<WeakReference<ClassLoader>> unloaded) {
        manager.preload(coordinates);
        try (PluginHandle.Lease lease = registry.acquire(coordinates.pluginId(), coordinates.version())) {
            assertNotSame(Stage3FixturePluginV1.class.getClassLoader(), lease.handle().classLoader());
            unloaded.add(new WeakReference<>(lease.handle().classLoader()));
            assertFalse(manager.release(coordinates.pluginId(), coordinates.version()));
        }
        assertTrue(manager.release(coordinates.pluginId(), coordinates.version()));
        assertFalse(manager.isLoaded(coordinates.pluginId(), coordinates.version()));
    }

    private CompiledConnectorPipeline compile(ConnectorPluginRegistry registry, String version,
                                               String pipelineVersion) throws Exception {
        PipelineCompiler compiler = new PipelineCompiler(
                registry, new DefaultPluginValidationContext(Clock.systemUTC(), "2.1.0", ref -> true), mapper);
        JsonNode config = mapper.createObjectNode();
        String hash = compiler.sha256(config);
        List<ConnectorStageDefinition> stages = List.of(
                stage("builder", StageCapability.REQUEST_BUILDER, version, 1, config, hash),
                stage("transport", StageCapability.TRANSPORT, version, 2, config, hash),
                stage("normalizer", StageCapability.RESPONSE_NORMALIZER, version, 3, config, hash));
        return compiler.compile(new ConnectorPipelineDefinition(pipelineVersion, "snapshot-" + version, stages));
    }

    private ConnectorStageDefinition stage(String key, StageCapability capability, String version, int order,
                                           JsonNode config, String hash) {
        return new ConnectorStageDefinition(key, capability, "stage3-fixture", version, order, true, config, hash);
    }

    private ConnectorPipelineExecutor executor() {
        var context = TestPluginContexts.context();
        return new ConnectorPipelineExecutor(
                Clock.systemUTC(), context.logger(), context.metrics());
    }

    private ConnectorExecutionRequest request() {
        return new ConnectorExecutionRequest(Map.of("probe", "stage3"), "stage3-vendor",
                Instant.now().plusSeconds(30), () -> false);
    }

    private PluginRuntimeManager manager(FixtureArtifacts artifacts, ConnectorPluginRegistry registry) {
        PluginArtifactVerifier verifier = new PluginArtifactVerifier(mapper, keyId -> Optional.of(
                new TrustedSigningKey(keyId, artifacts.keyPair().getPublic(), "Ed25519")));
        PluginLoader loader = new PluginLoader(TestPluginContexts.context(), "2.1.0", "1.0");
        return new PluginRuntimeManager(verifier, loader, registry);
    }

    private FixtureArtifacts createArtifacts() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        PluginArtifactCoordinates v1 = createArtifact(pair, "1.0.0", "Stage 3 Fixture V1",
                Stage3FixturePluginV1.class);
        PluginArtifactCoordinates v2 = createArtifact(pair, "2.0.0", "Stage 3 Fixture V2",
                Stage3FixturePluginV2.class);
        return new FixtureArtifacts(pair, v1, v2);
    }

    private PluginArtifactCoordinates createArtifact(KeyPair pair, String version, String displayName,
                                                     Class<?> entryClass) throws Exception {
        byte[] manifest = manifest(version, displayName, entryClass.getName());
        Path jar = createJar(version, manifest, entryClass);
        String sha = sha256(jar);
        String signature = sign(pair, manifest, sha);
        return new PluginArtifactCoordinates("stage3-fixture", version, jar, sha, signature, "stage3-key");
    }

    private PluginArtifactCoordinates dynamicallyCompiledArtifact(KeyPair pair, String version,
                                                                  String dependencyValue) throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("dynamic-src-" + version));
        Path classes = Files.createDirectories(tempDir.resolve("dynamic-classes-" + version));
        Path dependencySource = sourceRoot.resolve("fixture/shared/VersionedDependency.java");
        Path pluginSource = sourceRoot.resolve("fixture/dynamic/DependencyFixturePlugin.java");
        Files.createDirectories(dependencySource.getParent());
        Files.createDirectories(pluginSource.getParent());
        Files.writeString(dependencySource, """
                package fixture.shared;
                public final class VersionedDependency {
                    public String value() { return "%s"; }
                }
                """.formatted(dependencyValue));
        Files.writeString(pluginSource, """
                package fixture.dynamic;
                import com.dataplatform.plugin.spi.*;
                import com.fasterxml.jackson.databind.JsonNode;
                import fixture.shared.VersionedDependency;
                import java.util.*;
                public final class DependencyFixturePlugin implements ConnectorPlugin {
                    private boolean initialized;
                    public PluginDescriptor descriptor() {
                        return new PluginDescriptor("stage3-dependency-fixture", "%s", "1.0",
                            new VersionedDependency().value(), "test", Set.of(StageCapability.RESPONSE_PARSER));
                    }
                    public void initialize(PluginContext context) { initialized = true; }
                    public List<ConnectorStageFactory> stageFactories() { return List.of(new Factory()); }
                    public PluginSelfTestResult selfTest() {
                        return initialized ? PluginSelfTestResult.success() : PluginSelfTestResult.failure("not initialized");
                    }
                    public void close() { initialized = false; }
                    private static final class Factory implements ConnectorStageFactory {
                        public StageCapability capability() { return StageCapability.RESPONSE_PARSER; }
                        public void validate(JsonNode config, PluginValidationContext context) { }
                        public ConnectorStage create(CompiledStageConfig config) {
                            return new ConnectorStage() {
                                public StageCapability capability() { return StageCapability.RESPONSE_PARSER; }
                                public void execute(ConnectorExchange exchange, StageExecutionContext context) { }
                            };
                        }
                    }
                }
                """.formatted(version));
        int compilation = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-classpath", System.getProperty("java.class.path"), "-d", classes.toString(),
                dependencySource.toString(), pluginSource.toString());
        assertEquals(0, compilation, "dynamic plugin fixture must compile");

        String displayName = dependencyValue;
        byte[] manifest = ("""
                {"manifestVersion":"1","pluginId":"stage3-dependency-fixture","version":"%s","spiVersion":"1.0",
                 "displayName":"%s","provider":"test","entryClass":"fixture.dynamic.DependencyFixturePlugin",
                 "capabilities":["RESPONSE_PARSER"],"minHostVersion":"2.0.0",
                 "configSchema":{"type":"object"},
                 "permissions":{"networkProtocols":["https"],"networkHosts":["fixture.invalid"]}}
                """).formatted(version, displayName).getBytes(StandardCharsets.UTF_8);
        Path jar = tempDir.resolve("dependency-fixture-" + version + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(PluginManifestReader.MANIFEST_PATH));
            output.write(manifest);
            output.closeEntry();
            try (var files = Files.walk(classes)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String name = classes.relativize(file).toString().replace('\\', '/');
                    output.putNextEntry(new JarEntry(name));
                    output.write(Files.readAllBytes(file));
                    output.closeEntry();
                }
            }
        }
        String sha = sha256(jar);
        return new PluginArtifactCoordinates("stage3-dependency-fixture", version, jar, sha,
                sign(pair, manifest, sha), "stage3-key");
    }

    private byte[] manifest(String version, String displayName, String entryClass) {
        String value = """
                {"manifestVersion":"1","pluginId":"stage3-fixture","version":"%s","spiVersion":"1.0",
                 "displayName":"%s","provider":"test","entryClass":"%s",
                 "capabilities":["REQUEST_BUILDER","TRANSPORT","RESPONSE_NORMALIZER"],
                 "minHostVersion":"2.0.0","configSchema":{"type":"object"},
                 "permissions":{"networkProtocols":["https"],"networkHosts":["fixture.invalid"]}}
                """.formatted(version, displayName, entryClass);
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private Path createJar(String version, byte[] manifest, Class<?> entryClass) throws Exception {
        Path jar = tempDir.resolve("stage3-fixture-" + version + ".jar");
        Path classes = Path.of(entryClass.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(PluginManifestReader.MANIFEST_PATH));
            output.write(manifest);
            output.closeEntry();
            addClass(output, classes, entryClass);
        }
        return jar;
    }

    private void addClass(JarOutputStream output, Path classes, Class<?> type) throws IOException {
        String name = type.getName().replace('.', '/') + ".class";
        output.putNextEntry(new JarEntry(name));
        output.write(Files.readAllBytes(classes.resolve(name)));
        output.closeEntry();
    }

    private String sign(KeyPair pair, byte[] manifest, String sha) throws Exception {
        byte[] canonical = mapper.writeValueAsBytes(sort(mapper.readTree(manifest)));
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(pair.getPrivate());
        byte[] hash = sha.getBytes(StandardCharsets.US_ASCII);
        byte[] payload = new byte[canonical.length + 1 + hash.length];
        System.arraycopy(canonical, 0, payload, 0, canonical.length);
        payload[canonical.length] = '\n';
        System.arraycopy(hash, 0, payload, canonical.length + 1, hash.length);
        signature.update(payload);
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private JsonNode sort(JsonNode value) {
        if (value.isObject()) {
            var result = mapper.createObjectNode();
            List<String> fields = new ArrayList<>();
            value.fieldNames().forEachRemaining(fields::add);
            fields.sort(String::compareTo);
            fields.forEach(field -> result.set(field, sort(value.get(field))));
            return result;
        }
        if (value.isArray()) {
            var result = mapper.createArrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        return value;
    }

    private String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }

    private long metaspaceUsed() {
        return ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> "Metaspace".equals(pool.getName()))
                .map(MemoryPoolMXBean::getUsage)
                .mapToLong(usage -> usage.getUsed())
                .findFirst()
                .orElseThrow();
    }

    private void forceClassUnloading(List<WeakReference<ClassLoader>> references) throws InterruptedException {
        for (int attempt = 0; attempt < 20
                && references.stream().anyMatch(reference -> reference.get() != null); attempt++) {
            System.gc();
            Thread.sleep(25);
        }
    }

    private record FixtureArtifacts(KeyPair keyPair, PluginArtifactCoordinates v1,
                                    PluginArtifactCoordinates v2) { }

    private static final class ThrowingClosePlugin implements ConnectorPlugin {
        private final String version;
        private final boolean failOnClose;

        private ThrowingClosePlugin(String version, boolean failOnClose) {
            this.version = version;
            this.failOnClose = failOnClose;
        }

        @Override
        public PluginDescriptor descriptor() {
            return new PluginDescriptor("throwing-close-fixture", version, "1.0", "Throwing close", "test",
                    java.util.Set.of(StageCapability.RESPONSE_PARSER));
        }

        @Override public void initialize(PluginContext context) { }
        @Override public List<ConnectorStageFactory> stageFactories() { return List.of(new ParserFactory()); }
        @Override public PluginSelfTestResult selfTest() { return PluginSelfTestResult.success(); }
        @Override public void close() {
            if (failOnClose) throw new IllegalStateException("expected close failure");
        }
    }

    private static final class ParserFactory implements ConnectorStageFactory {
        @Override public StageCapability capability() { return StageCapability.RESPONSE_PARSER; }
        @Override public void validate(JsonNode config, PluginValidationContext context) { }
        @Override public ConnectorStage create(CompiledStageConfig config) {
            return new ConnectorStage() {
                @Override public StageCapability capability() { return StageCapability.RESPONSE_PARSER; }
                @Override public void execute(ConnectorExchange exchange, StageExecutionContext context) { }
            };
        }
    }
}
