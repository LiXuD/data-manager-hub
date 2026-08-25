package com.dataplatform.access.connector.artifact;

import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Downloads immutable plugin artifacts into an Access-owned, hash-addressed local cache. */
@Component
public class ConnectorPluginArtifactCache {

    static final long MAX_ARTIFACT_BYTES = 50L * 1024L * 1024L;

    private final ConnectorRuntimeProperties properties;
    private final HttpClient httpClient;
    private final AtomicLong cacheBytes = new AtomicLong();

    @Autowired
    public ConnectorPluginArtifactCache(
            ConnectorRuntimeProperties properties, MeterRegistry meterRegistry) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), meterRegistry);
    }

    ConnectorPluginArtifactCache(ConnectorRuntimeProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), Metrics.globalRegistry);
    }

    ConnectorPluginArtifactCache(ConnectorRuntimeProperties properties, HttpClient httpClient) {
        this(properties, httpClient, Metrics.globalRegistry);
    }

    ConnectorPluginArtifactCache(
            ConnectorRuntimeProperties properties, HttpClient httpClient, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.httpClient = httpClient;
        meterRegistry.gauge("connector_artifact_cache_bytes", cacheBytes);
    }

    public Path resolve(PluginArtifactDescriptorDTO artifact) {
        validateCoordinates(artifact.pluginId(), artifact.version(), artifact.artifactSha256());
        URI uri = validateArtifactUri(artifact.artifactUri());
        Path target = cacheTarget(artifact);
        try {
            if (Files.isRegularFile(target) && hashMatches(target, artifact.artifactSha256())) {
                refreshCacheBytes();
                return target;
            }
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "download-", ".jar.part");
            try {
                download(uri, temporary);
                if (!hashMatches(temporary, artifact.artifactSha256())) {
                    throw new IllegalStateException("Downloaded plugin artifact SHA-256 mismatch");
                }
                atomicMove(temporary, target);
                refreshCacheBytes();
                return target;
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Plugin artifact cache operation failed", ex);
        }
    }

    private URI validateArtifactUri(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("artifactUri is required");
        }
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                || !StringUtils.hasText(uri.getHost()) || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Only user-info-free HTTPS artifact URIs are allowed");
        }
        boolean allowed = properties.getRepositoryAllowedPrefixes().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(prefix -> allowedByPrefix(uri, prefix));
        if (!allowed) {
            throw new IllegalArgumentException("Artifact URI is outside configured repository prefixes");
        }
        return uri;
    }

    private boolean allowedByPrefix(URI artifact, String prefixValue) {
        try {
            URI prefix = URI.create(prefixValue).normalize();
            URI candidate = artifact.normalize();
            if (!"https".equalsIgnoreCase(prefix.getScheme()) || prefix.getUserInfo() != null
                    || !StringUtils.hasText(prefix.getHost()) || prefix.getQuery() != null
                    || prefix.getFragment() != null || !prefix.getHost().equalsIgnoreCase(candidate.getHost())
                    || effectivePort(prefix) != effectivePort(candidate)) {
                return false;
            }
            String prefixPath = normalizedPath(prefix.getPath());
            String candidatePath = normalizedPath(candidate.getPath());
            return candidatePath.equals(prefixPath) || candidatePath.startsWith(
                    prefixPath.endsWith("/") ? prefixPath : prefixPath + "/");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : 443;
    }

    private String normalizedPath(String path) {
        if (!StringUtils.hasText(path)) return "/";
        return path.startsWith("/") ? path : "/" + path;
    }

    private Path cacheTarget(PluginArtifactDescriptorDTO artifact) {
        Path root = Path.of(properties.getCacheDirectory()).toAbsolutePath().normalize();
        Path target = root.resolve(artifact.pluginId())
                .resolve(artifact.version())
                .resolve(artifact.artifactSha256().toLowerCase())
                .resolve("connector-plugin.jar")
                .normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Unsafe plugin cache coordinates");
        }
        return target;
    }

    private void download(URI uri, Path target) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/java-archive, application/octet-stream")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IllegalStateException("Artifact repository returned HTTP " + response.statusCode());
        }
        long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (declaredLength > MAX_ARTIFACT_BYTES) {
            response.body().close();
            throw new IllegalStateException("Plugin artifact exceeds 50 MiB");
        }
        try (InputStream input = response.body(); var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_ARTIFACT_BYTES) {
                    throw new IllegalStateException("Plugin artifact exceeds 50 MiB");
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private boolean hashMatches(Path path, String expected) {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            byte[] actual = HexFormat.of().formatHex(digest.digest()).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(actual,
                    expected.toLowerCase().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            return false;
        }
    }

    private void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void validateCoordinates(String pluginId, String version, String sha256) {
        if (pluginId == null || !pluginId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("Invalid pluginId");
        }
        if (version == null || !version.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")) {
            throw new IllegalArgumentException("Invalid pluginVersion");
        }
        if (sha256 == null || !sha256.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid artifact SHA-256");
        }
    }

    private void refreshCacheBytes() {
        Path root = Path.of(properties.getCacheDirectory()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            cacheBytes.set(0L);
            return;
        }
        try (var files = Files.walk(root)) {
            cacheBytes.set(files.filter(Files::isRegularFile).mapToLong(path -> {
                try { return Files.size(path); } catch (IOException ignored) { return 0L; }
            }).sum());
        } catch (IOException ignored) {
            // Cache accounting must never make a verified artifact unavailable.
        }
    }
}
