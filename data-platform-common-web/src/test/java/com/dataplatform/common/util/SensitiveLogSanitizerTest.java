package com.dataplatform.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveLogSanitizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void redactsNestedJsonCredentialsWithoutChangingBusinessFields() throws Exception {
        String input = "{\"username\":\"admin\",\"password\":\"plain\","
                + "\"auth\":{\"accessToken\":\"jwt-value\",\"api_secret\":\"secret-value\"},"
                + "\"resolvedSecrets\":{\"vendor.secretKey\":\"runtime-secret\"},"
                + "\"items\":[{\"client-secret\":\"client-value\",\"companyName\":\"Acme\"}]}";

        JsonNode sanitized = objectMapper.readTree(SensitiveLogSanitizer.sanitizeBody(input, objectMapper));

        assertThat(sanitized.path("username").asText()).isEqualTo("admin");
        assertThat(sanitized.path("password").asText()).isEqualTo("***");
        assertThat(sanitized.path("auth").path("accessToken").asText()).isEqualTo("***");
        assertThat(sanitized.path("auth").path("api_secret").asText()).isEqualTo("***");
        assertThat(sanitized.path("resolvedSecrets").asText()).isEqualTo("***");
        assertThat(sanitized.path("items").path(0).path("client-secret").asText()).isEqualTo("***");
        assertThat(sanitized.path("items").path(0).path("companyName").asText()).isEqualTo("Acme");
    }

    @Test
    void redactsFormAndQueryCredentials() {
        assertThat(SensitiveLogSanitizer.sanitizeBody(
                "clientSecret=dev-secret&scope=masterdata", objectMapper))
                .isEqualTo("clientSecret=***&scope=masterdata");
        assertThat(SensitiveLogSanitizer.sanitizeQueryString(
                "api_key=raw-key&format=json&accessToken=raw-token&vendor.secretKey=raw-secret"))
                .isEqualTo("api_key=***&format=json&accessToken=***&vendor.secretKey=***");
    }
}
