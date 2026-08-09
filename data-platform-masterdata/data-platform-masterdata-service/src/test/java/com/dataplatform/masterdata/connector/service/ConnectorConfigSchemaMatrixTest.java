package com.dataplatform.masterdata.connector.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectorConfigSchemaMatrixTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConnectorConfigSchemaValidator validator = new ConnectorConfigSchemaValidator(mapper);

    @Test
    void validatesAllSupportedTypesEnumsArraysAndSecretReferences() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","required":["name","count","ratio","enabled","mode","secret"],
                 "additionalProperties":false,"properties":{
                   "name":{"type":"string","minLength":2,"maxLength":8},
                   "count":{"type":"integer","minimum":1,"maximum":10},
                   "ratio":{"type":"number"},"enabled":{"type":"boolean"},
                   "mode":{"type":"string","enum":["SYNC","ASYNC"]},
                   "secret":{"type":"string","x-secret-ref":true},
                   "tags":{"type":"array","items":{"type":"string"}},
                   "nested":{"type":"object","properties":{"port":{"type":"integer"}}}
                 }}
                """);
        Map<String, Object> valid = Map.of(
                "name", "demo", "count", 2, "ratio", 1.5, "enabled", true,
                "mode", "SYNC", "secret", "vendor.secretRef", "tags", List.of("a", "b"),
                "nested", Map.of("port", 443));
        assertTrue(validator.validate(schema, valid).isEmpty());

        Map<String, Object> invalid = Map.of(
                "name", "x", "count", 11.5, "ratio", "bad", "enabled", "true",
                "mode", "OTHER", "secret", "", "tags", List.of(1), "extra", true);
        List<String> errors = validator.validate(schema, invalid);
        assertTrue(errors.stream().anyMatch(error -> error.contains("$.count类型必须为integer")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("$.ratio类型必须为number")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("$.enabled类型必须为boolean")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("枚举值")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("非空密钥引用")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("$.tags[0]类型必须为string")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("未声明字段")));
    }

    @Test
    void rejectsMalformedSchemaShapesAndInvalidPatterns() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","properties":{
                  "badType":{"type":"null"},
                  "badPattern":{"type":"string","pattern":"["},
                  "script":{"type":"string"}
                }}
                """);
        List<String> errors = validator.validate(schema,
                Map.of("badType", "x", "badPattern", "value", "script", "code"));

        assertTrue(errors.stream().anyMatch(error -> error.contains("type不受支持")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("pattern无效")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("动态代码")));
    }

    @Test
    void resolvesNonRecursiveLocalPointersAndRejectsReferenceCycles() throws Exception {
        var nonRecursive = mapper.readTree("""
                {"type":"object","$defs":{"endpoint":{"type":"string","pattern":"https://.*"}},
                 "properties":{"endpoint":{"$ref":"#/$defs/endpoint"}}}
                """);
        assertTrue(validator.validate(nonRecursive, Map.of("endpoint", "https://api.example.com")).isEmpty());
        assertTrue(validator.validate(nonRecursive, Map.of("endpoint", 443)).stream()
                .anyMatch(error -> error.contains("类型必须为string")));

        for (String schema : List.of(
                "{\"$ref\":\"#\"}",
                "{\"$defs\":{\"a\":{\"$ref\":\"#/$defs/b\"},\"b\":{\"$ref\":\"#/$defs/a\"}},\"$ref\":\"#/$defs/a\"}",
                "{\"type\":\"array\",\"items\":{\"$ref\":\"#\"}}")) {
            assertTrue(validator.validate(mapper.readTree(schema), Map.of()).stream()
                    .anyMatch(error -> error.contains("递归本地$ref")));
        }

        var dynamic = mapper.readTree("{\"$dynamicRef\":\"#node\"}");
        assertTrue(validator.validate(dynamic, Map.of()).stream()
                .anyMatch(error -> error.contains("动态或递归引用")));
    }

    @Test
    void rejectsPlaintextSensitiveFieldsAndMissingOrCrossVendorReferences() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","properties":{
                  "password":{"type":"object","x-sensitive":true},
                  "apiToken":{"type":"object"},
                  "certificate":{"type":"object","x-secret-ref":true}
                }}
                """);
        Map<String, Object> valid = Map.of(
                "password", Map.of("secretRef", "vendor.password"),
                "apiToken", Map.of("secretRef", "vendor.token"),
                "certificate", Map.of("secretRef", "vendor.certificate"));
        assertTrue(validator.validate(schema, valid,
                ref -> ref.startsWith("vendor.")).isEmpty());

        Map<String, Object> plaintext = Map.of(
                "password", "clear-text", "apiToken", "token-value",
                "certificate", Map.of("secretRef", "other-vendor.certificate"));
        List<String> errors = validator.validate(schema, plaintext,
                ref -> ref.startsWith("vendor."));
        assertTrue(errors.stream().anyMatch(error -> error.contains("$.password是敏感字段，只能保存{secretRef}")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("$.apiToken是敏感字段，只能保存{secretRef}")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("不存在或不属于当前厂商")));
    }
}
