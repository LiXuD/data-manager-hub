package com.dataplatform.masterdata.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

@Component
public class ConnectorConfigSchemaValidator {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "string", "integer", "number", "boolean", "object", "array");
    private static final Set<String> EXECUTABLE_FIELD_NAMES = Set.of(
            "script", "javascript", "groovy", "spel", "expression", "code");

    private final ObjectMapper objectMapper;

    public ConnectorConfigSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> validate(JsonNode schema, Map<String, Object> config) {
        List<String> errors = new ArrayList<>();
        validateSchemaShape(schema, "$schema", errors, new HashSet<>());
        validateValue(schema, objectMapper.valueToTree(config == null ? Map.of() : config), "$", errors);
        return List.copyOf(errors);
    }

    private void validateSchemaShape(JsonNode schema, String path, List<String> errors, Set<JsonNode> visited) {
        if (schema == null || !schema.isObject() || !visited.add(schema)) {
            if (schema == null || !schema.isObject()) errors.add(path + "必须是Schema对象");
            return;
        }
        JsonNode ref = schema.get("$ref");
        if (ref != null && (!ref.isTextual() || !ref.asText().startsWith("#/"))) {
            errors.add(path + "禁止远程$ref");
        }
        JsonNode type = schema.get("type");
        if (type != null && (!type.isTextual() || !SUPPORTED_TYPES.contains(type.asText()))) {
            errors.add(path + ".type不受支持");
        }
        JsonNode properties = schema.get("properties");
        if (properties != null) {
            if (!properties.isObject()) {
                errors.add(path + ".properties必须是对象");
            } else {
                properties.fields().forEachRemaining(entry -> {
                    if (EXECUTABLE_FIELD_NAMES.contains(entry.getKey().toLowerCase())) {
                        errors.add(path + ".properties禁止动态代码字段: " + entry.getKey());
                    }
                    validateSchemaShape(entry.getValue(), path + ".properties." + entry.getKey(), errors, visited);
                });
            }
        }
        JsonNode items = schema.get("items");
        if (items != null) validateSchemaShape(items, path + ".items", errors, visited);
    }

    private void validateValue(JsonNode schema, JsonNode value, String path, List<String> errors) {
        if (schema == null || !schema.isObject()) return;
        String type = schema.path("type").asText(null);
        if (type != null && !matches(type, value)) {
            errors.add(path + "类型必须为" + type);
            return;
        }
        JsonNode enumValues = schema.get("enum");
        if (enumValues != null && enumValues.isArray()) {
            boolean matched = false;
            for (JsonNode candidate : enumValues) {
                if (candidate.equals(value)) matched = true;
            }
            if (!matched) errors.add(path + "不在允许的枚举值中");
        }
        if (value.isObject()) validateObject(schema, value, path, errors);
        if (value.isArray()) validateArray(schema, value, path, errors);
        if (value.isTextual()) validateString(schema, value.asText(), path, errors);
        if (value.isNumber()) validateNumber(schema, value.decimalValue(), path, errors);
    }

    private void validateObject(JsonNode schema, JsonNode value, String path, List<String> errors) {
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            required.forEach(field -> {
                if (!field.isTextual() || !value.has(field.asText()) || value.get(field.asText()).isNull()) {
                    errors.add(path + "." + field.asText() + "为必填项");
                }
            });
        }
        JsonNode properties = schema.path("properties");
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode propertySchema = properties.get(field.getKey());
            if (propertySchema != null) {
                validateValue(propertySchema, field.getValue(), path + "." + field.getKey(), errors);
            } else if (schema.has("additionalProperties") && !schema.path("additionalProperties").asBoolean(true)) {
                errors.add(path + "包含未声明字段: " + field.getKey());
            }
        }
    }

    private void validateArray(JsonNode schema, JsonNode value, String path, List<String> errors) {
        JsonNode items = schema.get("items");
        if (items != null) {
            for (int index = 0; index < value.size(); index++) {
                validateValue(items, value.get(index), path + "[" + index + "]", errors);
            }
        }
    }

    private void validateString(JsonNode schema, String value, String path, List<String> errors) {
        if (schema.path("x-secret-ref").asBoolean(false) && value.isBlank()) {
            errors.add(path + "必须是非空密钥引用");
        }
        if (schema.has("minLength") && value.length() < schema.path("minLength").asInt()) {
            errors.add(path + "长度小于minLength");
        }
        if (schema.has("maxLength") && value.length() > schema.path("maxLength").asInt()) {
            errors.add(path + "长度超过maxLength");
        }
        if (schema.has("pattern")) {
            try {
                if (!Pattern.compile(schema.path("pattern").asText()).matcher(value).matches()) {
                    errors.add(path + "不符合pattern");
                }
            } catch (PatternSyntaxException exception) {
                errors.add(path + "对应的Schema pattern无效");
            }
        }
    }

    private void validateNumber(JsonNode schema, BigDecimal value, String path, List<String> errors) {
        if (schema.has("minimum") && value.compareTo(schema.path("minimum").decimalValue()) < 0) {
            errors.add(path + "小于minimum");
        }
        if (schema.has("maximum") && value.compareTo(schema.path("maximum").decimalValue()) > 0) {
            errors.add(path + "大于maximum");
        }
    }

    private boolean matches(String type, JsonNode value) {
        return switch (type) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            default -> false;
        };
    }
}
