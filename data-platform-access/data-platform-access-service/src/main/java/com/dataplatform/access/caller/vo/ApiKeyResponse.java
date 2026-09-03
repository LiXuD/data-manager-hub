package com.dataplatform.access.caller.vo;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.common.enums.ApiKeyStatus;
import java.time.LocalDateTime;

/**
 * HTTP response for an API key. The clear-text key is only included by the
 * one-time create response; all later responses contain a masked value.
 */
public record ApiKeyResponse(
        Long id,
        Long callerId,
        String keyName,
        String apiKey,
        Boolean rateLimitEnabled,
        Integer rateLimit,
        Long quotaLimit,
        Long quotaUsed,
        ApiKeyStatus status,
        LocalDateTime expireTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ApiKeyResponse created(ApiKey source) {
        return from(source, true);
    }

    public static ApiKeyResponse view(ApiKey source) {
        return from(source, false);
    }

    private static ApiKeyResponse from(ApiKey source, boolean reveal) {
        if (source == null) {
            return null;
        }
        return new ApiKeyResponse(
                source.getId(),
                source.getCallerId(),
                source.getKeyName(),
                reveal ? source.getApiKey() : mask(source.getApiKey()),
                source.getRateLimitEnabled(),
                source.getRateLimit(),
                source.getQuotaLimit(),
                source.getQuotaUsed(),
                source.getStatus(),
                source.getExpireTime(),
                source.getCreatedAt(),
                source.getUpdatedAt());
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 8) {
            return "••••••••";
        }
        return value.substring(0, 3) + "••••" + value.substring(value.length() - 4);
    }
}
