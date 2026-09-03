package com.dataplatform.common.plugin.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dataplatform.plugin.spi.StageCapability;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConnectorJsonSchemaValidatorSecretReferenceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConnectorJsonSchemaValidator validator = new ConnectorJsonSchemaValidator();

    @Test
    void derivesStringAndObjectReferencesFromTheSignedSchemaOnly() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","properties":{
                  "signingMaterial":{"type":"string","x-secret-ref":true},
                  "password":{"type":"object","x-sensitive":true},
                  "untrustedText":{"type":"string"}
                }}
                """);
        var config = mapper.readTree("""
                {"signingMaterial":"vendor.signing","password":{"secretRef":"vendor.password"},
                 "untrustedText":"not-a-secret-ref"}
                """);

        assertEquals(Set.of("vendor.signing", "vendor.password"),
                validator.secretReferences(schema, config));
    }

    @Test
    void derivesOnlySecretReferencesScopedToTheCurrentV2Capability() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","properties":{
                  "builderSecret":{"type":"string","x-secret-ref":true,
                    "x-stage-scope":["REQUEST_BUILDER"]},
                  "parserSecret":{"type":"string","x-secret-ref":true,
                    "x-stage-scope":["RESPONSE_PARSER"]}
                }}
                """);
        var config = mapper.readTree("""
                {"builderSecret":"vendor.builder","parserSecret":"vendor.parser"}
                """);

        assertEquals(Set.of("vendor.builder"), validator.secretReferences(
                schema, config, StageCapability.REQUEST_BUILDER));
        assertEquals(Set.of("vendor.parser"), validator.secretReferences(
                schema, config, StageCapability.RESPONSE_PARSER));
        assertEquals(Set.of(), validator.secretReferences(
                schema, config, StageCapability.TRANSPORT));
    }

    @Test
    void appliesTypeAndStringConstraintsToExplicitSecretReferences() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","properties":{
                  "secret":{"type":"string","minLength":3,"maxLength":12,
                    "pattern":"^vendor\\\\.[a-z-]+$","x-secret-ref":true}
                }}
                """);

        assertEquals(List.of(), validator.validate(schema,
                mapper.readTree("{\"secret\":\"vendor.key\"}"), ignored -> true));
        assertFalse(validator.validate(schema,
                mapper.readTree("{\"secret\":\"other.key\"}"), ignored -> true).isEmpty());
        assertFalse(validator.validate(schema,
                mapper.readTree("{\"secret\":\"vendor.this-reference-is-too-long\"}"), ignored -> true)
                .isEmpty());
        assertFalse(validator.validate(schema,
                mapper.readTree("{\"secret\":{\"secretRef\":\"vendor.key\"}}"), ignored -> true)
                .isEmpty());
    }

    @Test
    void validatesTheReferencePropertyInsideObjectSecretRepresentations() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","properties":{
                  "certificate":{"type":"object","x-secret-ref":true,
                    "properties":{"secretRef":{"type":"string","minLength":3,
                      "pattern":"^vendor\\\\.[a-z-]+$"}},"additionalProperties":false}
                }}
                """);

        assertEquals(List.of(), validator.validate(schema,
                mapper.readTree("{\"certificate\":{\"secretRef\":\"vendor.cert\"}}"), ignored -> true));
        assertFalse(validator.validate(schema,
                mapper.readTree("{\"certificate\":{\"secretRef\":\"other.cert\"}}"), ignored -> true)
                .isEmpty());
    }

    @Test
    void protectsAdditionalLegacySensitiveFieldNames() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","properties":{
                  "secretKey":{"type":"object"},"apiSecret":{"type":"object"}
                }}
                """);

        assertFalse(validator.validate(schema,
                mapper.readTree("{\"secretKey\":{\"value\":\"plaintext\"}}"), ignored -> true)
                .isEmpty());
        assertEquals(List.of(), validator.validate(schema,
                mapper.readTree("{\"apiSecret\":{\"secretRef\":\"vendor.api-secret\"}}"), ignored -> true));
    }
}
