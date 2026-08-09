package com.dataplatform.masterdata.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dataplatform.common.plugin.schema.ConnectorJsonSchemaValidator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class ConnectorConfigSchemaValidator {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "string", "integer", "number", "boolean", "object", "array");
    private static final Set<String> EXECUTABLE_FIELD_NAMES = Set.of(
            "script", "javascript", "groovy", "spel", "expression", "code");

    private final ObjectMapper objectMapper;
    private final ConnectorJsonSchemaValidator delegate = new ConnectorJsonSchemaValidator();

    public ConnectorConfigSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> validate(JsonNode schema, Map<String, Object> config) {
        return validate(schema, config, ignored -> true);
    }

    public List<String> validate(JsonNode schema, Map<String, Object> config,
                                 Predicate<String> secretReferenceExists) {
        return delegate.validate(schema,
                objectMapper.valueToTree(config == null ? Map.of() : config), secretReferenceExists);
    }

    private void validateSchemaShape(JsonNode schema, String path, List<String> errors, Set<JsonNode> visited) {
        if (schema == null || !schema.isObject() || !visited.add(schema)) {
            if (schema == null || !schema.isObject()) errors.add(path + "必须是Schema对象");
            return;
        }
        JsonNode ref = schema.get("$ref");
        if (ref != null && (!ref.isTextual() || !isLocalJsonPointer(ref.asText()))) {
            errors.add(path + "禁止远程$ref");
        }
        if (schema.has("$dynamicRef") || schema.has("$recursiveRef")) {
            errors.add(path + "禁止动态或递归引用");
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
        for (String definitionsField : List.of("$defs", "definitions")) {
            JsonNode definitions = schema.get(definitionsField);
            if (definitions != null && definitions.isObject()) {
                definitions.fields().forEachRemaining(entry -> validateSchemaShape(
                        entry.getValue(), path + "." + definitionsField + "." + entry.getKey(), errors, visited));
            }
        }
    }

    private void validateValue(JsonNode root, JsonNode schema, JsonNode value,
                               String path, List<String> errors) {
        if (schema == null || !schema.isObject()) return;
        JsonNode reference = schema.get("$ref");
        if (reference != null && reference.isTextual() && isLocalJsonPointer(reference.asText())) {
            validateValue(root, resolveLocalPointer(root, reference.asText()), value, path, errors);
        }
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
        if (value.isObject()) validateObject(root, schema, value, path, errors);
        if (value.isArray()) validateArray(root, schema, value, path, errors);
        if (value.isTextual()) validateString(schema, value.asText(), path, errors);
        if (value.isNumber()) validateNumber(schema, value.decimalValue(), path, errors);
    }

    private void validateObject(JsonNode root, JsonNode schema, JsonNode value,
                                String path, List<String> errors) {
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
                validateValue(root, propertySchema, field.getValue(), path + "." + field.getKey(), errors);
            } else if (schema.has("additionalProperties") && !schema.path("additionalProperties").asBoolean(true)) {
                errors.add(path + "包含未声明字段: " + field.getKey());
            }
        }
    }

    private void validateArray(JsonNode root, JsonNode schema, JsonNode value,
                               String path, List<String> errors) {
        JsonNode items = schema.get("items");
        if (items != null) {
            for (int index = 0; index < value.size(); index++) {
                validateValue(root, items, value.get(index), path + "[" + index + "]", errors);
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

    private boolean validateReferenceGraph(JsonNode root, List<String> errors) {
        if (root == null || !root.isObject()) {
            return true;
        }
        Set<JsonNode> visiting = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<JsonNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int before = errors.size();
        visitReferenceGraph(root, root, "$schema", visiting, visited, errors);
        return errors.size() == before;
    }

    private void visitReferenceGraph(JsonNode root, JsonNode node, String path,
                                     Set<JsonNode> visiting, Set<JsonNode> visited,
                                     List<String> errors) {
        if (node == null || node.isValueNode() || visited.contains(node)) return;
        if (!visiting.add(node)) {
            errors.add(path + "包含递归本地$ref");
            return;
        }
        if (node.isObject()) {
            if (node.has("$dynamicRef") || node.has("$recursiveRef")) {
                errors.add(path + "禁止动态或递归引用");
            }
            JsonNode reference = node.get("$ref");
            if (reference != null) {
                if (!reference.isTextual() || !isLocalJsonPointer(reference.asText())) {
                    errors.add(path + "禁止远程或不受支持的$ref");
                } else {
                    JsonNode target = resolveLocalPointer(root, reference.asText());
                    if (target == null) {
                        errors.add(path + "本地$ref无法解析");
                    } else {
                        visitReferenceGraph(root, target, path + ".$ref", visiting, visited, errors);
                    }
                }
            }
            node.fields().forEachRemaining(entry -> {
                if (!"$ref".equals(entry.getKey())) {
                    visitReferenceGraph(root, entry.getValue(), path + "." + entry.getKey(),
                            visiting, visited, errors);
                }
            });
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                visitReferenceGraph(root, node.get(index), path + "[" + index + "]",
                        visiting, visited, errors);
            }
        }
        visiting.remove(node);
        visited.add(node);
    }

    private boolean isLocalJsonPointer(String reference) {
        return "#".equals(reference) || reference.startsWith("#/");
    }

    private JsonNode resolveLocalPointer(JsonNode root, String reference) {
        JsonNode target = "#".equals(reference) ? root : root.at(reference.substring(1));
        return target.isMissingNode() ? null : target;
    }
}
