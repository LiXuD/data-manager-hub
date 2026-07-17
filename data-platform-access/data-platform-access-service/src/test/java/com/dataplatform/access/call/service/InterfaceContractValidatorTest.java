package com.dataplatform.access.call.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterfaceContractValidatorTest {

    @Test
    void appliesDefaultsAndValidatesNestedArrayFields() {
        InterfaceParamDTO pageSize = field("pageSize", "integer", false);
        pageSize.setDefaultValue("20");
        pageSize.setConstraintConfig("{\"minimum\":1,\"maximum\":100}");

        InterfaceParamDTO name = field("name", "string", true);
        name.setConstraintConfig("{\"minLength\":2}");
        InterfaceParamDTO items = field("items", "array", true);
        items.setChildren(List.of(name));

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("items", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("name", "Alice")))));

        InterfaceContractValidator.ValidationResult result = InterfaceContractValidator.validate(
                List.of(pageSize, items), values, true);

        assertTrue(result.valid());
        assertEquals(20L, values.get("pageSize"));
    }

    @Test
    void reportsPreciseNestedPathAndConstraintFailure() {
        InterfaceParamDTO score = field("score", "number", true);
        score.setConstraintConfig("{\"minimum\":60}");
        InterfaceParamDTO profile = field("profile", "object", true);
        profile.setChildren(List.of(score));

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("profile", new LinkedHashMap<>(Map.of("score", 20)));

        InterfaceContractValidator.ValidationResult result = InterfaceContractValidator.validate(
                List.of(profile), values, false);

        assertFalse(result.valid());
        assertTrue(result.firstError().contains("profile.score"));
    }

    @Test
    void rejectsInvalidLegacyConstraintAndBooleanDefaultInsteadOfBypassingValidation() {
        InterfaceParamDTO enabled = field("enabled", "boolean", false);
        enabled.setDefaultValue("not-boolean");
        InterfaceParamDTO verified = field("verified", "boolean", true);
        verified.setConstraintConfig("not-json");
        Map<String, Object> values = new LinkedHashMap<>(Map.of("verified", true));

        InterfaceContractValidator.ValidationResult result = InterfaceContractValidator.validate(
                List.of(enabled, verified), values, true);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("类型必须为boolean")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("约束配置不是有效JSON")));
    }

    @Test
    void validatesConfiguredStringFormats() {
        InterfaceParamDTO email = field("email", "string", true);
        email.setConstraintConfig("{\"format\":\"email\"}");

        InterfaceContractValidator.ValidationResult invalid = InterfaceContractValidator.validate(
                List.of(email), new LinkedHashMap<>(Map.of("email", "not-an-email")), false);
        InterfaceContractValidator.ValidationResult valid = InterfaceContractValidator.validate(
                List.of(email), new LinkedHashMap<>(Map.of("email", "user@example.com")), false);

        assertFalse(invalid.valid());
        assertTrue(invalid.firstError().contains("email格式"));
        assertTrue(valid.valid());
    }

    @Test
    void validatesPrimitiveArrayItemTypesWithPreciseIndex() {
        InterfaceParamDTO tags = field("tags", "array", true);
        tags.setArrayItemType("string");

        InterfaceContractValidator.ValidationResult invalid = InterfaceContractValidator.validate(
                List.of(tags), new LinkedHashMap<>(Map.of("tags", List.of("a", 2))), false);
        InterfaceContractValidator.ValidationResult valid = InterfaceContractValidator.validate(
                List.of(tags), new LinkedHashMap<>(Map.of("tags", List.of("a", "b"))), false);

        assertFalse(invalid.valid());
        assertTrue(invalid.firstError().contains("tags[1]"));
        assertTrue(valid.valid());
    }

    private InterfaceParamDTO field(String name, String type, boolean required) {
        InterfaceParamDTO field = new InterfaceParamDTO();
        field.setParamName(name);
        field.setParamType(type);
        field.setRequired(required);
        return field;
    }
}
