package com.dataplatform.common.security;

import com.dataplatform.common.security.dto.InternalTokenRequest;
import com.dataplatform.common.security.dto.InternalTokenResponse;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class ServiceTokenProvider {

    private static final long REFRESH_SKEW_SECONDS = 30;

    private final InternalSecurityProperties properties;
    private final RestClient restClient;
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    public ServiceTokenProvider(InternalSecurityProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    public String getToken(String audience) {
        CachedToken current = cache.get(audience);
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(REFRESH_SKEW_SECONDS))) {
            return current.value();
        }
        synchronized (cache) {
            current = cache.get(audience);
            if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(REFRESH_SKEW_SECONDS))) {
                return current.value();
            }
            InternalTokenResponse response = requestToken(audience);
            CachedToken refreshed = new CachedToken(response.getAccessToken(),
                    Instant.now().plusSeconds(response.getExpiresIn()));
            cache.put(audience, refreshed);
            return refreshed.value();
        }
    }

    private InternalTokenResponse requestToken(String audience) {
        RestClientException lastFailure = null;
        int attempts = Math.max(1, properties.getTokenMaxAttempts());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return requestTokenOnce(audience);
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().is4xxClientError()) {
                    throw exception;
                }
                lastFailure = exception;
            } catch (RestClientException exception) {
                lastFailure = exception;
            }
            if (attempt < attempts) {
                waitBeforeRetry(attempt);
            }
        }
        throw new IllegalStateException("Unable to obtain an internal service token", lastFailure);
    }

    private InternalTokenResponse requestTokenOnce(String audience) {
        InternalTokenRequest request = new InternalTokenRequest();
        request.setServiceName(properties.getServiceName());
        request.setClientSecret(properties.getClientSecret());
        request.setAudience(audience);
        InternalTokenResponse response = restClient.post()
                .uri(properties.getTokenUri())
                .body(request)
                .retrieve()
                .body(InternalTokenResponse.class);
        if (response == null || response.getAccessToken() == null || response.getExpiresIn() <= 0) {
            throw new IllegalStateException("Identity service returned an empty internal token");
        }
        return response;
    }

    private void waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(properties.getTokenRetryBackoff().toMillis() * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while obtaining an internal service token", exception);
        }
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
