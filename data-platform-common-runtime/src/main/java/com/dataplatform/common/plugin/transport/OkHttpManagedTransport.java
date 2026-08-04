package com.dataplatform.common.plugin.transport;

import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.ManagedHttpTransport;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.StageExecutionContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.Dns;
import okhttp3.EventListener;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class OkHttpManagedTransport implements ManagedHttpTransport {

    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> FORBIDDEN_HEADERS = Set.of("host", "content-length", "connection", "upgrade");
    private final OkHttpClient baseClient;
    private final NetworkPolicy policy;

    public OkHttpManagedTransport(OkHttpClient baseClient, NetworkPolicy policy) {
        this.baseClient = baseClient;
        this.policy = policy;
    }

    @Override
    public ConnectorRawResponse execute(ConnectorRequest request, StageExecutionContext context)
            throws ConnectorException {
        validateUrl(request.url());
        if (context.cancellationRequested()) {
            throw failure(ErrorCategory.PLUGIN_INTERNAL_ERROR, "REQUEST_CANCELLED",
                    "Connector execution was cancelled", RequestDeliveryState.NOT_SENT, null);
        }
        Duration remaining = context.remainingTime();
        if (remaining.isZero()) {
            throw failure(ErrorCategory.TRANSPORT_TIMEOUT, "EXECUTION_DEADLINE_EXCEEDED",
                    "Connector execution deadline was exceeded", RequestDeliveryState.NOT_SENT, null);
        }
        List<InetAddress> resolved = resolveAndValidate(request.url().getHost());
        AtomicBoolean sendStarted = new AtomicBoolean();
        OkHttpClient client = client(request, remaining, request.url().getHost(), resolved, sendStarted);
        Request okhttpRequest = buildRequest(request);
        Instant started = context.clock().instant();
        try (Response response = client.newCall(okhttpRequest).execute()) {
            byte[] body = readResponse(response.body(), Math.min(request.maxResponseBytes(), policy.maxResponseBytes()));
            long sent = request.body().length;
            return new ConnectorRawResponse(response.code(), response.headers().toMultimap(), body,
                    Duration.between(started, context.clock().instant()), response.request().url().uri(),
                    sent, body.length);
        } catch (java.net.SocketTimeoutException exception) {
            throw failure(ErrorCategory.TRANSPORT_TIMEOUT, "TRANSPORT_TIMEOUT", "Vendor request timed out",
                    sendStarted.get() ? RequestDeliveryState.MAYBE_SENT : RequestDeliveryState.NOT_SENT, exception);
        } catch (IOException exception) {
            throw failure(ErrorCategory.TRANSPORT_CONNECTION_ERROR, "TRANSPORT_CONNECTION_ERROR",
                    "Vendor connection failed",
                    sendStarted.get() ? RequestDeliveryState.MAYBE_SENT : RequestDeliveryState.NOT_SENT, exception);
        }
    }

    private OkHttpClient client(ConnectorRequest request, Duration remaining, String host,
                                List<InetAddress> resolved, AtomicBoolean sendStarted) {
        Duration connect = minimum(request.connectTimeout(), policy.maxConnectTimeout(), remaining);
        Duration read = minimum(request.readTimeout(), policy.maxReadTimeout(), remaining);
        Duration total = minimum(request.totalTimeout(), policy.maxTotalTimeout(), remaining);
        Dns pinnedDns = hostname -> hostname.equalsIgnoreCase(host) ? resolved : Dns.SYSTEM.lookup(hostname);
        return baseClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .dns(pinnedDns)
                .connectTimeout(connect.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(read.toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(read.toMillis(), TimeUnit.MILLISECONDS)
                .callTimeout(total.toMillis(), TimeUnit.MILLISECONDS)
                .eventListener(new EventListener() {
                    @Override public void requestHeadersStart(Call call) { sendStarted.set(true); }
                    @Override public void requestBodyStart(Call call) { sendStarted.set(true); }
                })
                .build();
    }

    private Request buildRequest(ConnectorRequest request) throws ConnectorException {
        HttpUrl parsed = HttpUrl.get(request.url());
        HttpUrl.Builder url = parsed.newBuilder();
        request.query().forEach((key, values) -> values.forEach(value -> url.addQueryParameter(key, value)));
        Request.Builder builder = new Request.Builder().url(url.build());
        for (var entry : request.headers().entrySet()) {
            String key = entry.getKey();
            if (FORBIDDEN_HEADERS.contains(key.toLowerCase(Locale.ROOT))) {
                throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "FORBIDDEN_TRANSPORT_HEADER",
                        "Request contains a transport-controlled header", RequestDeliveryState.NOT_SENT, null);
            }
            entry.getValue().forEach(value -> builder.addHeader(key, value));
        }
        String method = request.method();
        if ("GET".equals(method) || "HEAD".equals(method)) {
            builder.method(method, null);
        } else if (BODY_METHODS.contains(method)) {
            MediaType mediaType = MediaType.parse(request.contentType());
            builder.method(method, RequestBody.create(request.body(), mediaType));
        } else {
            throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "UNSUPPORTED_HTTP_METHOD",
                    "Unsupported HTTP method", RequestDeliveryState.NOT_SENT, null);
        }
        return builder.build();
    }

    private void validateUrl(URI uri) throws ConnectorException {
        if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getHost() == null) {
            throw failure(ErrorCategory.CONFIGURATION_ERROR, "UNSAFE_VENDOR_URL",
                    "Vendor URL is not allowed", RequestDeliveryState.NOT_SENT, null);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!policy.allowedProtocols().contains(scheme) || !hostAllowed(host)) {
            throw failure(ErrorCategory.CONFIGURATION_ERROR, "VENDOR_ENDPOINT_NOT_ALLOWED",
                    "Vendor endpoint is outside the network allowlist", RequestDeliveryState.NOT_SENT, null);
        }
    }

    private boolean hostAllowed(String host) {
        return policy.allowedHosts().stream().anyMatch(allowed -> allowed.equals(host)
                || allowed.startsWith("*.") && host.endsWith(allowed.substring(1))
                && host.length() > allowed.length() - 1);
    }

    private List<InetAddress> resolveAndValidate(String host) throws ConnectorException {
        try {
            List<InetAddress> addresses = Dns.SYSTEM.lookup(host);
            if (addresses.isEmpty()) {
                throw new UnknownHostException(host);
            }
            if (!policy.allowPrivateNetworks() && addresses.stream().anyMatch(this::isPrivate)) {
                throw failure(ErrorCategory.CONFIGURATION_ERROR, "PRIVATE_NETWORK_FORBIDDEN",
                        "Vendor endpoint resolves to a private network", RequestDeliveryState.NOT_SENT, null);
            }
            return List.copyOf(addresses);
        } catch (ConnectorException exception) {
            throw exception;
        } catch (UnknownHostException exception) {
            throw failure(ErrorCategory.TRANSPORT_CONNECTION_ERROR, "VENDOR_HOST_UNRESOLVED",
                    "Vendor host could not be resolved", RequestDeliveryState.NOT_SENT, exception);
        }
    }

    private boolean isPrivate(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0 || first == 10 || first == 127 || first == 169 && second == 254
                    || first == 172 && second >= 16 && second <= 31 || first == 192 && second == 168
                    || first >= 224;
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) == 0xfc || first == 0xff;
        }
        return true;
    }

    private byte[] readResponse(ResponseBody body, long limit) throws IOException, ConnectorException {
        if (body == null) {
            return new byte[0];
        }
        if (body.contentLength() > limit) {
            throw failure(ErrorCategory.TRANSPORT_HTTP_ERROR, "RESPONSE_TOO_LARGE",
                    "Vendor response exceeds the configured size limit", RequestDeliveryState.SENT, null);
        }
        try (InputStream input = body.byteStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) {
                    throw failure(ErrorCategory.TRANSPORT_HTTP_ERROR, "RESPONSE_TOO_LARGE",
                            "Vendor response exceeds the configured size limit", RequestDeliveryState.SENT, null);
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private Duration minimum(Duration... durations) {
        Duration minimum = durations[0];
        for (Duration duration : durations) {
            if (duration.compareTo(minimum) < 0) {
                minimum = duration;
            }
        }
        return minimum.isZero() ? Duration.ofMillis(1) : minimum;
    }

    private ConnectorException failure(ErrorCategory category, String code, String message,
                                       RequestDeliveryState delivery, Throwable cause) {
        return new ConnectorException(category, code, message, delivery, cause);
    }
}
