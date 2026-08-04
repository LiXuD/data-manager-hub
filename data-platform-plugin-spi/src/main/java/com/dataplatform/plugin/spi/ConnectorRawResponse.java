package com.dataplatform.plugin.spi;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ConnectorRawResponse(
        int statusCode,
        Map<String, List<String>> headers,
        byte[] body,
        Duration latency,
        URI remoteEndpoint,
        long bytesSent,
        long bytesReceived) {

    public ConnectorRawResponse {
        headers = headers == null ? Map.of() : headers.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())));
        body = body == null ? new byte[0] : body.clone();
        latency = Objects.requireNonNull(latency, "latency");
        remoteEndpoint = Objects.requireNonNull(remoteEndpoint, "remoteEndpoint");
        if (bytesSent < 0 || bytesReceived < 0) {
            throw new IllegalArgumentException("byte counts cannot be negative");
        }
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
