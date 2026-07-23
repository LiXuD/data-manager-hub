package com.dataplatform.common.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.common.security.pipeline.SecurityDirection;
import com.dataplatform.common.security.pipeline.SecurityExecutionContext;
import com.dataplatform.common.security.pipeline.SecurityPipelineExecutor;
import com.dataplatform.common.security.pipeline.SecurityStepConfig;
import com.dataplatform.common.security.pipeline.SecurityStepType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class HttpVendorAdapterSecurityPipelineTest {

    @Test
    void shouldApplyRequestHeaderSignatureAndResponseDecryption() throws Exception {
        String key = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        SecurityStepConfig encrypt = step("encrypt", SecurityDirection.REQUEST, SecurityStepType.ENCRYPT, 100,
                Map.of("inputFrom", "BODY", "algorithm", "AES_GCM", "secretRef", "aes.key",
                        "keyEncoding", "BASE64", "outputEncoding", "BASE64", "prependIv", true));
        SecurityExecutionContext encryptionContext = new SecurityExecutionContext(SecurityDirection.REQUEST,
                Map.of(), new LinkedHashMap<>(), new LinkedHashMap<>(), Map.of("aes.key", key));
        encryptionContext.setBody("{\"payload\":\"vendor-secret\"}");
        new SecurityPipelineExecutor().execute(SecurityDirection.REQUEST, List.of(encrypt), encryptionContext);
        String encryptedPayload = String.valueOf(encryptionContext.getResults().get("encrypt"));

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "text/plain")
                    .setBody(encryptedPayload));
            server.start();

            SecurityStepConfig digest = step("digest", SecurityDirection.REQUEST, SecurityStepType.DIGEST, 100,
                    Map.of("inputFrom", "BODY", "algorithm", "SHA256", "outputEncoding", "HEX_LOWER"));
            SecurityStepConfig header = step("header", SecurityDirection.REQUEST, SecurityStepType.INJECT, 200,
                    Map.of("inputFrom", "digest", "location", "HEADER", "fieldName", "X-Request-Digest"));
            SecurityStepConfig decrypt = step("decrypt", SecurityDirection.RESPONSE, SecurityStepType.DECRYPT, 100,
                    Map.of("inputFrom", "BODY", "algorithm", "AES_GCM", "secretRef", "aes.key",
                            "keyEncoding", "BASE64", "inputEncoding", "BASE64", "prependIv", true));
            SecurityStepConfig inject = step("plain", SecurityDirection.RESPONSE, SecurityStepType.INJECT, 200,
                    Map.of("inputFrom", "decrypt", "location", "BODY"));

            VendorAdapterConfig config = new VendorAdapterConfig();
            config.setApiUrl(server.url("/secure-api").toString());
            config.setMethod("PUT");
            config.setSecuritySteps(List.of(inject, digest, decrypt, header));
            config.setResolvedSecrets(Map.of("aes.key", key));

            Map<String, Object> result = new HttpVendorAdapter("TEST").execute(config, Map.of("name", "Alice"));

            assertEquals(true, result.get("success"));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertEquals("vendor-secret", data.get("payload"));
            RecordedRequest request = server.takeRequest();
            assertEquals("PUT", request.getMethod());
            assertNotNull(request.getHeader("X-Request-Digest"));
            assertEquals(sha256("{\"name\":\"Alice\"}"), request.getHeader("X-Request-Digest"));
        }
    }

    @Test
    void shouldFailClosedForUnknownAuthenticationType() {
        VendorAdapterConfig config = new VendorAdapterConfig();
        config.setApiUrl("https://example.invalid/vendor");
        config.setMethod("POST");
        config.setAuthType("UNKNOWN_AUTH");
        config.setAuthConfig(Map.of());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new HttpVendorAdapter("TEST").execute(config, Map.of("name", "Alice")));

        assertTrue(exception.getMessage().contains("未知的认证类型"));
    }

    @Test
    void shouldTreatPersistedNoneAuthenticationAsNoAuthentication() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"ok\":true}"));
            server.start();
            VendorAdapterConfig config = new VendorAdapterConfig();
            config.setApiUrl(server.url("/public-api").toString());
            config.setMethod("GET");
            config.setAuthType("NONE");

            Map<String, Object> result = new HttpVendorAdapter("PUBLIC")
                    .execute(config, Map.of("city", "Asia/Shanghai"));

            assertEquals(true, result.get("success"));
            assertEquals("Asia/Shanghai", server.takeRequest().getRequestUrl().queryParameter("city"));
        }
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private SecurityStepConfig step(String id, SecurityDirection direction, SecurityStepType type,
                                    int sortNo, Map<String, Object> config) {
        SecurityStepConfig step = new SecurityStepConfig();
        step.setId(id);
        step.setDirection(direction);
        step.setStepType(type);
        step.setSortNo(sortNo);
        step.setEnabled(true);
        step.setConfig(config);
        return step;
    }
}
