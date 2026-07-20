package com.dataplatform.common.security.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecurityPipelineExecutorTest {

    private final SecurityPipelineExecutor executor = new SecurityPipelineExecutor();

    @Test
    void shouldCanonicalizeHmacAndInjectHeaderInConfiguredOrder() {
        SecurityStepConfig canonical = step("canonical", SecurityDirection.REQUEST,
                SecurityStepType.CANONICALIZE, 100, Map.of(
                        "inputFrom", "PARAMS",
                        "fields", List.of("timestamp", "appId"),
                        "fieldOrder", "EXPLICIT",
                        "pairSeparator", "&",
                        "keyValueSeparator", "="));
        SecurityStepConfig hmac = step("signature", SecurityDirection.REQUEST,
                SecurityStepType.HMAC, 200, Map.of(
                        "inputFrom", "canonical",
                        "algorithm", "HMAC_SHA256",
                        "secretRef", "vendor.sign.key",
                        "outputEncoding", "HEX_UPPER"));
        SecurityStepConfig inject = step("header", SecurityDirection.REQUEST,
                SecurityStepType.INJECT, 300, Map.of(
                        "inputFrom", "signature",
                        "location", "HEADER",
                        "fieldName", "X-Signature"));
        SecurityExecutionContext context = context(SecurityDirection.REQUEST,
                new LinkedHashMap<>(Map.of("appId", "demo", "timestamp", 123L)),
                Map.of("vendor.sign.key", "secret"));

        executor.execute(SecurityDirection.REQUEST, List.of(inject, hmac, canonical), context);

        assertEquals("timestamp=123&appId=demo", context.getResults().get("canonical"));
        assertTrue(context.getHeaders().get("X-Signature").matches("[0-9A-F]{64}"));
    }

    @Test
    void shouldRoundTripAesGcmAndSm4Cbc() {
        String aesKey = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        assertRoundTrip("AES_GCM", aesKey, "BASE64");
        assertRoundTrip("AES_CBC", aesKey, "BASE64");
        assertRoundTrip("SM4_CBC", "0123456789abcdeffedcba9876543210", "HEX");
    }

    @Test
    void shouldRoundTripRsaOaepSha256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        Map<String, String> secrets = Map.of(
                "rsa.private", Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                "rsa.public", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        SecurityStepConfig encrypt = step("encrypted", SecurityDirection.REQUEST, SecurityStepType.ENCRYPT, 100,
                Map.of("inputFrom", "PARAMS.payload", "algorithm", "RSA_OAEP",
                        "secretRef", "rsa.public", "outputEncoding", "BASE64"));
        SecurityExecutionContext request = context(SecurityDirection.REQUEST,
                new LinkedHashMap<>(Map.of("payload", "sensitive-data")), secrets);
        executor.execute(SecurityDirection.REQUEST, List.of(encrypt), request);
        SecurityStepConfig decrypt = step("decrypted", SecurityDirection.RESPONSE, SecurityStepType.DECRYPT, 100,
                Map.of("inputFrom", "PARAMS.payload", "algorithm", "RSA_OAEP",
                        "secretRef", "rsa.private", "inputEncoding", "BASE64"));
        SecurityExecutionContext response = context(SecurityDirection.RESPONSE,
                new LinkedHashMap<>(Map.of("payload", request.getResults().get("encrypted"))), secrets);

        executor.execute(SecurityDirection.RESPONSE, List.of(decrypt), response);

        assertEquals("sensitive-data", response.getResults().get("decrypted"));
    }

    @Test
    void shouldSupportConfiguredDigestAlgorithms() {
        Map<String, Integer> lengths = Map.of("MD5", 32, "SHA1", 40, "SHA256", 64, "SHA512", 128, "SM3", 64);
        lengths.forEach((algorithm, length) -> {
            SecurityStepConfig digest = step("digest", SecurityDirection.REQUEST, SecurityStepType.DIGEST, 100,
                    Map.of("inputFrom", "PARAMS.payload", "algorithm", algorithm,
                            "outputEncoding", "HEX_LOWER"));
            SecurityExecutionContext context = context(SecurityDirection.REQUEST,
                    new LinkedHashMap<>(Map.of("payload", "test-data")), Map.of());
            executor.execute(SecurityDirection.REQUEST, List.of(digest), context);
            assertEquals(length, String.valueOf(context.getResults().get("digest")).length());
        });
    }

    @Test
    void shouldSignAndVerifyRsaSha256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        Map<String, String> secrets = Map.of(
                "rsa.private", Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                "rsa.public", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));

        SecurityStepConfig sign = step("sign", SecurityDirection.REQUEST, SecurityStepType.SIGN, 100,
                Map.of("inputFrom", "PARAMS.payload", "algorithm", "RSA_SHA256",
                        "secretRef", "rsa.private", "outputEncoding", "BASE64"));
        SecurityExecutionContext request = context(SecurityDirection.REQUEST,
                new LinkedHashMap<>(Map.of("payload", "bank-data")), secrets);
        executor.execute(SecurityDirection.REQUEST, List.of(sign), request);
        String signature = String.valueOf(request.getResults().get("sign"));

        SecurityStepConfig verify = step("verify", SecurityDirection.RESPONSE, SecurityStepType.VERIFY, 100,
                Map.of("inputFrom", "PARAMS.payload", "signatureFrom", "PARAMS.signature",
                        "algorithm", "RSA_SHA256", "secretRef", "rsa.public"));
        SecurityExecutionContext response = context(SecurityDirection.RESPONSE,
                new LinkedHashMap<>(Map.of("payload", "bank-data", "signature", signature)), secrets);

        executor.execute(SecurityDirection.RESPONSE, List.of(verify), response);
        assertEquals(true, response.getResults().get("verify"));
    }

    @Test
    void shouldVerifyHmacResponseWithoutTimingSensitiveComparison() {
        Map<String, String> secrets = Map.of("hmac.key", "response-secret");
        SecurityStepConfig hmac = step("hmac", SecurityDirection.REQUEST, SecurityStepType.HMAC, 100,
                Map.of("inputFrom", "PARAMS.payload", "algorithm", "HMAC_SHA256",
                        "secretRef", "hmac.key", "outputEncoding", "HEX_LOWER"));
        SecurityExecutionContext request = context(SecurityDirection.REQUEST,
                new LinkedHashMap<>(Map.of("payload", "response-data")), secrets);
        executor.execute(SecurityDirection.REQUEST, List.of(hmac), request);
        SecurityStepConfig verify = step("verify", SecurityDirection.RESPONSE, SecurityStepType.VERIFY, 100,
                Map.of("inputFrom", "PARAMS.payload", "signatureFrom", "PARAMS.signature",
                        "algorithm", "HMAC_SHA256", "secretRef", "hmac.key",
                        "signatureEncoding", "HEX_LOWER"));
        SecurityExecutionContext response = context(SecurityDirection.RESPONSE,
                new LinkedHashMap<>(Map.of("payload", "response-data",
                        "signature", request.getResults().get("hmac"))), secrets);

        executor.execute(SecurityDirection.RESPONSE, List.of(verify), response);

        assertEquals(true, response.getResults().get("verify"));
    }

    @Test
    void shouldGenerateRemoveAndRejectInvalidDependency() {
        SecurityStepConfig generate = step("nonce", SecurityDirection.REQUEST, SecurityStepType.GENERATE, 100,
                Map.of("generator", "NONCE", "length", 20, "location", "PARAM", "fieldName", "nonce"));
        SecurityStepConfig remove = step("cleanup", SecurityDirection.REQUEST, SecurityStepType.REMOVE_FIELD, 200,
                Map.of("location", "PARAM", "fieldName", "temporary"));
        SecurityExecutionContext context = context(SecurityDirection.REQUEST,
                new LinkedHashMap<>(Map.of("temporary", "value")), Map.of());
        executor.execute(SecurityDirection.REQUEST, List.of(generate, remove), context);
        assertTrue(String.valueOf(context.getParams().get("nonce")).matches("[0-9a-f]{20}"));
        assertEquals(null, context.getParams().get("temporary"));

        SecurityStepConfig invalid = step("broken", SecurityDirection.REQUEST, SecurityStepType.DIGEST, 100,
                Map.of("inputFrom", "missing", "algorithm", "SHA256"));
        assertThrows(IllegalArgumentException.class,
                () -> executor.validate(SecurityDirection.REQUEST, List.of(invalid)));
    }

    @Test
    void shouldApplyCanonicalNullPolicyWithoutFailingOnNullValues() {
        SecurityStepConfig keep = step("canonical", SecurityDirection.REQUEST,
                SecurityStepType.CANONICALIZE, 100, Map.of(
                        "inputFrom", "PARAMS", "fieldOrder", "KEY_ASC", "nullPolicy", "KEEP"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("empty", null);
        params.put("value", "ok");
        SecurityExecutionContext context = context(SecurityDirection.REQUEST, params, Map.of());

        executor.execute(SecurityDirection.REQUEST, List.of(keep), context);

        assertEquals("empty=&value=ok", context.getResults().get("canonical"));
    }

    @Test
    void shouldRejectIncompleteOrOversizedPipelineConfiguration() {
        SecurityStepConfig constant = step("constant", SecurityDirection.REQUEST,
                SecurityStepType.GENERATE, 100, Map.of(
                        "generator", "CONSTANT", "fieldName", "channel", "location", "PARAM"));
        SecurityStepConfig explicit = step("canonical", SecurityDirection.REQUEST,
                SecurityStepType.CANONICALIZE, 100, Map.of("fieldOrder", "EXPLICIT"));
        SecurityStepConfig oversized = step("oversized", SecurityDirection.REQUEST,
                SecurityStepType.DIGEST, 100, Map.of("algorithm", "SHA256", "padding", "x".repeat(70_000)));

        assertThrows(IllegalArgumentException.class,
                () -> executor.validate(SecurityDirection.REQUEST, List.of(constant)));
        assertThrows(IllegalArgumentException.class,
                () -> executor.validate(SecurityDirection.REQUEST, List.of(explicit)));
        assertThrows(IllegalArgumentException.class,
                () -> executor.validate(SecurityDirection.REQUEST, List.of(oversized)));
    }

    @Test
    void shouldRejectVerifySignatureReferenceThatHasNotBeenProduced() {
        SecurityStepConfig verify = step("verify", SecurityDirection.RESPONSE, SecurityStepType.VERIFY, 100,
                Map.of("inputFrom", "RESPONSE", "signatureFrom", "missingStep",
                        "algorithm", "HMAC_SHA256", "secretRef", "key"));

        assertThrows(IllegalArgumentException.class,
                () -> executor.validate(SecurityDirection.RESPONSE, List.of(verify)));
    }

    private void assertRoundTrip(String algorithm, String secret, String keyEncoding) {
        SecurityStepConfig encrypt = step("encrypted", SecurityDirection.REQUEST, SecurityStepType.ENCRYPT, 100,
                Map.of("inputFrom", "PARAMS.payload", "algorithm", algorithm, "secretRef", "crypto.key",
                        "keyEncoding", keyEncoding, "outputEncoding", "BASE64", "prependIv", true));
        SecurityExecutionContext request = context(SecurityDirection.REQUEST,
                new LinkedHashMap<>(Map.of("payload", "sensitive-data")), Map.of("crypto.key", secret));
        executor.execute(SecurityDirection.REQUEST, List.of(encrypt), request);
        String encrypted = String.valueOf(request.getResults().get("encrypted"));
        assertNotEquals("sensitive-data", encrypted);

        SecurityStepConfig decrypt = step("decrypted", SecurityDirection.RESPONSE, SecurityStepType.DECRYPT, 100,
                Map.of("inputFrom", "PARAMS.payload", "algorithm", algorithm, "secretRef", "crypto.key",
                        "keyEncoding", keyEncoding, "inputEncoding", "BASE64", "prependIv", true));
        SecurityExecutionContext response = context(SecurityDirection.RESPONSE,
                new LinkedHashMap<>(Map.of("payload", encrypted)), Map.of("crypto.key", secret));
        executor.execute(SecurityDirection.RESPONSE, List.of(decrypt), response);
        assertEquals("sensitive-data", response.getResults().get("decrypted"));
    }

    private SecurityExecutionContext context(SecurityDirection direction, Map<String, Object> params,
                                             Map<String, String> secrets) {
        return new SecurityExecutionContext(direction, params, new LinkedHashMap<>(), new LinkedHashMap<>(), secrets);
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
