package com.dataplatform.masterdata.connector.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectorConfigSchemaValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConnectorConfigSchemaValidator validator = new ConnectorConfigSchemaValidator(mapper);

    @Test
    void validatesSupportedObjectSchema() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","required":["endpoint","timeout"],"additionalProperties":false,
                 "properties":{"endpoint":{"type":"string","pattern":"https://.*"},
                 "timeout":{"type":"integer","minimum":100},
                 "secret":{"type":"string","x-secret-ref":true}}}
                """);
        assertTrue(validator.validate(schema, Map.of(
                "endpoint", "https://api.example.com", "timeout", 500, "secret", "vendor.secretKey")).isEmpty());
        assertFalse(validator.validate(schema, Map.of("endpoint", "http://bad", "timeout", 10)).isEmpty());
    }

    @Test
    void rejectsExecutableConfigurationFieldsAndRemoteReferences() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","properties":{"script":{"type":"string"},
                 "child":{"$ref":"https://evil.example/schema"}}}
                """);
        var errors = validator.validate(schema, Map.of("script", "return true"));
        assertTrue(errors.stream().anyMatch(error -> error.contains("动态代码")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("远程$ref")));
    }
}
