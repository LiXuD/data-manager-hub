package com.dataplatform.plugin.spi;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ConnectorRequest(
        String method,
        URI url,
        Map<String, List<String>> headers,
        Map<String, List<String>> query,
        String contentType,
        byte[] body,
        Duration connectTimeout,
        Duration readTimeout,
        Duration totalTimeout,
        IdempotencyPolicy idempotencyPolicy,
        String idempotencyKey,
        long maxResponseBytes) {

    public ConnectorRequest {
        method = requireText(method, "method").toUpperCase();
        url = Objects.requireNonNull(url, "url");
        headers = immutableMultiMap(headers);
        query = immutableMultiMap(query);
        contentType = contentType == null ? "application/json; charset=utf-8" : contentType;
        body = body == null ? new byte[0] : body.clone();
        connectTimeout = positive(connectTimeout, "connectTimeout");
        readTimeout = positive(readTimeout, "readTimeout");
        totalTimeout = positive(totalTimeout, "totalTimeout");
        idempotencyPolicy = Objects.requireNonNull(idempotencyPolicy, "idempotencyPolicy");
        if (idempotencyPolicy == IdempotencyPolicy.IDEMPOTENT_WITH_KEY
                && (idempotencyKey == null || idempotencyKey.isBlank())) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        if (maxResponseBytes <= 0) {
            throw new IllegalArgumentException("maxResponseBytes must be positive");
        }
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    private static Map<String, List<String>> immutableMultiMap(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return source.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    private static Duration positive(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
