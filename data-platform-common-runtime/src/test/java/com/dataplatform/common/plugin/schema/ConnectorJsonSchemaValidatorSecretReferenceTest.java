package com.dataplatform.common.plugin.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
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
}
