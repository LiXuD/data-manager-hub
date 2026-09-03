package com.dataplatform.common.plugin.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ConnectorJsonSchemaValidatorConditionalTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConnectorJsonSchemaValidator validator = new ConnectorJsonSchemaValidator();

    @Test
    void enforcesConditionalRequiredFieldsFromTheSignedSchema() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","additionalProperties":false,
                 "properties":{"flow":{"type":"string"},"endpoint":{"type":"string"},
                 "tokenEndpoint":{"type":"string"}},
                 "if":{"properties":{"flow":{"const":"single-http"}},"required":["flow"]},
                 "then":{"required":["endpoint"]},
                 "else":{"required":["tokenEndpoint"]}}
                """);

        assertFalse(validator.validate(schema, mapper.readTree("{\"flow\":\"single-http\"}"))
                .isEmpty());
        assertTrue(validator.validate(schema, mapper.readTree(
                "{\"flow\":\"single-http\",\"endpoint\":\"https://example.test\"}"))
                .isEmpty());
        assertFalse(validator.validate(schema, mapper.readTree("{\"flow\":\"token-business\"}"))
                .isEmpty());
    }

    @Test
    void rejectsResidualFieldsHiddenByTheSameVisibilityConditionUsedByTheUi() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","additionalProperties":false,
                 "properties":{"flow":{"type":"string"},
                 "endpoint":{"type":"string","x-ui-visible-if":{"flow":"single-http"}},
                 "tokenEndpoint":{"type":"string","x-ui-visible-if":{"field":"flow","equals":"token-business"}}}}
                """);

        assertTrue(validator.validate(schema, mapper.readTree(
                "{\"flow\":\"token-business\"}")).isEmpty());
        assertFalse(validator.validate(schema, mapper.readTree(
                "{\"flow\":\"token-business\",\"endpoint\":\"https://hidden.example\"}")).isEmpty());
    }

    @Test
    void evaluatesAllOfConditionalBranchesAndForbiddenFields() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","additionalProperties":false,
                 "properties":{"mode":{"type":"string"},"refValue":{"type":"string"},"user":{"type":"string"}},
                 "allOf":[
                   {"if":{"properties":{"mode":{"const":"TOKEN"}}},
                    "then":{"required":["refValue"],"not":{"anyOf":[{"required":["user"]}]}}},
                   {"if":{"properties":{"mode":{"const":"USER"}}},
                    "then":{"required":["user"],"not":{"anyOf":[{"required":["refValue"]}]}}}
                 ]}
                """);

        assertFalse(validator.validate(schema, mapper.readTree(
                "{\"mode\":\"TOKEN\"}")).isEmpty());
        var validTokenConfig = validator.validate(schema, mapper.readTree(
                "{\"mode\":\"TOKEN\",\"refValue\":\"ref\"}"));
        assertTrue(validTokenConfig.isEmpty(), () -> validTokenConfig.toString());
        assertFalse(validator.validate(schema, mapper.readTree(
                "{\"mode\":\"TOKEN\",\"refValue\":\"ref\",\"user\":\"alice\"}")).isEmpty());
    }

    @Test
    void validatesSchemaObjectsUsedByAdditionalProperties() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","additionalProperties":{"type":"string"},
                 "properties":{"known":{"type":"string"}}}
                """);

        assertTrue(validator.validate(schema, mapper.readTree(
                "{\"known\":\"ok\",\"extension\":\"also-ok\"}")).isEmpty());
        assertFalse(validator.validate(schema, mapper.readTree(
                "{\"extension\":42}")).isEmpty());
    }

    @Test
    void rejectsUnsupportedConditionalKeywordsInsteadOfIgnoringThem() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","properties":{"mode":{"type":"string"}},
                 "if":{"properties":{"mode":{"minimum":1}}},"then":{"required":["mode"]}}
                """);

        assertFalse(validator.validate(schema, mapper.readTree("{\"mode\":\"x\"}")).isEmpty());
    }

    @Test
    void enforcesArrayObjectAndConstantConstraints() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","additionalProperties":false,
                 "properties":{"kind":{"const":"fixed"},
                 "items":{"type":"array","minItems":1,"maxItems":2,"uniqueItems":true,
                 "items":{"type":"string"}}}}
                """);

        assertTrue(validator.validate(schema, mapper.readTree(
                "{\"kind\":\"fixed\",\"items\":[\"a\",\"b\"]}")).isEmpty());
        assertFalse(validator.validate(schema, mapper.readTree(
                "{\"kind\":\"other\",\"items\":[\"a\",\"a\"]}")).isEmpty());
        assertFalse(validator.validate(schema, mapper.readTree(
                "{\"kind\":\"fixed\",\"items\":[\"a\",\"b\",\"c\"]}")).isEmpty());
    }

    @Test
    void treatsMalformedLocalReferencesAsSchemaErrorsInsteadOfThrowing() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","properties":{"value":{"$ref":"#/properties/bad~2pointer"}}}
                """);

        assertFalse(validator.validate(schema, mapper.readTree("{\"value\":\"x\"}")).isEmpty());
    }

    @Test
    void appliesJsonSchemaPatternAndUnicodeLengthSemantics() throws Exception {
        var schema = mapper.readTree("""
                {"type":"string","minLength":1,"maxLength":24,"pattern":"https://"}
                """);

        assertTrue(validator.validate(schema, mapper.readTree("\"😀https://example.test\"" )).isEmpty());
        assertFalse(validator.validate(schema, mapper.readTree("\"x\"" )).isEmpty());
    }
}
