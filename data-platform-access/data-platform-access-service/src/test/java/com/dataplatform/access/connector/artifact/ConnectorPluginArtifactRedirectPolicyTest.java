package com.dataplatform.access.connector.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectorPluginArtifactRedirectPolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsRepositoryRedirectWithoutIssuingASecondRequest() {
        ConnectorRuntimeProperties properties = new ConnectorRuntimeProperties();
        properties.setCacheDirectory(tempDir.toString());
        properties.setRepositoryAllowedPrefixes(List.of("https://repo.example/plugins/"));
        RedirectClient client = new RedirectClient();
        ConnectorPluginArtifactCache cache = new ConnectorPluginArtifactCache(properties, client);
        PluginArtifactDescriptorDTO artifact = new PluginArtifactDescriptorDTO(
                "demo", "1.0.0", "1.0", "example.Demo", "https://repo.example/plugins/demo.jar",
                "a".repeat(64), "signature", "key-1", "{}", "{}", List.of("TRANSPORT"),
                "{}", "1.0.0", "VERIFIED");

        assertThrows(IllegalStateException.class, () -> cache.resolve(artifact));

        assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
        assertEquals(1, client.requests.get());
    }

    private static final class RedirectClient extends HttpClient {
        private final AtomicInteger requests = new AtomicInteger();

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(1)); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            requests.incrementAndGet();
            return (HttpResponse<T>) new RedirectResponse(request);
        }

        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record RedirectResponse(HttpRequest request) implements HttpResponse<InputStream> {
        @Override public int statusCode() { return 302; }
        @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() {
            return HttpHeaders.of(Map.of("Location", List.of("https://evil.example/plugin.jar")),
                    (name, value) -> true);
        }
        @Override public InputStream body() { return new ByteArrayInputStream(new byte[0]); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
