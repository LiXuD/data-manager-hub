package com.dataplatform.common.plugin.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.dataplatform.plugin.spi.StageCapability;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Host-owned JSON Schema 2020-12 subset shared by control and runtime planes. */
public final class ConnectorJsonSchemaValidator {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "string", "integer", "number", "boolean", "object", "array");
    private static final Set<String> EXECUTABLE_FIELDS = Set.of(
            "script", "javascript", "groovy", "spel", "expression", "code");
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "password", "passwd", "token", "accesstoken", "refreshtoken", "secret",
            "clientsecret", "apitoken", "apikey", "privatekey", "certificate", "cert", "credential");

    public List<String> validate(JsonNode schema, JsonNode config) {
        return validate(schema, config, ignored -> true);
    }

    public List<String> validate(JsonNode schema, JsonNode config, Predicate<String> secretReferenceExists) {
        List<String> errors = new ArrayList<>();
        Predicate<String> lookup = secretReferenceExists == null ? ignored -> false : secretReferenceExists;
        boolean refsValid = validateReferenceGraph(schema, errors);
        validateSchemaShape(schema, "$schema", errors, new HashSet<>());
        if (refsValid) validateValue(schema, schema, config, "$", null, lookup, errors);
        return List.copyOf(errors);
    }

    /** Collects only references selected by signed Schema fields after successful validation. */
    public Set<String> secretReferences(JsonNode schema, JsonNode config) {
        Set<String> result = new java.util.LinkedHashSet<>();
        collectSecretReferences(schema, schema, config, null, null, result);
        return Set.copyOf(result);
    }

    /** Collects only v2 secret references explicitly scoped to one capability. */
    public Set<String> secretReferences(
            JsonNode schema,
            JsonNode config,
            StageCapability capability) {
        Set<String> result = new java.util.LinkedHashSet<>();
        collectSecretReferences(schema, schema, config, null,
                java.util.Objects.requireNonNull(capability, "capability"), result);
        return Set.copyOf(result);
    }

    private void collectSecretReferences(JsonNode root, JsonNode schema, JsonNode value,
                                         String fieldName, StageCapability scopedCapability,
                                         Set<String> result) {
        if (schema == null || !schema.isObject() || value == null || value.isMissingNode() || value.isNull()) return;
        JsonNode reference = schema.get("$ref");
        if (reference != null && reference.isTextual() && isLocalPointer(reference.asText())) {
            collectSecretReferences(root, resolve(root, reference.asText()), value, fieldName,
                    scopedCapability, result);
            return;
        }
        boolean explicitSecretReference = schema.path("x-secret-ref").asBoolean(false);
        boolean legacySensitiveReference = schema.path("x-sensitive").asBoolean(false)
                || isSensitiveName(fieldName);
        if (explicitSecretReference || scopedCapability == null && legacySensitiveReference) {
            if (scopedCapability != null && !scopeContains(schema.path("x-stage-scope"), scopedCapability)) {
                return;
            }
            String secretReference = textualSecretReference(value);
            if (secretReference != null) result.add(secretReference);
            return;
        }
        if (value.isObject()) {
            JsonNode properties = schema.path("properties");
            value.fields().forEachRemaining(entry -> {
                JsonNode childSchema = properties.get(entry.getKey());
                if (childSchema != null) {
                    collectSecretReferences(root, childSchema, entry.getValue(), entry.getKey(),
                            scopedCapability, result);
                }
            });
        } else if (value.isArray() && schema.has("items")) {
            value.forEach(item -> collectSecretReferences(root, schema.get("items"), item, fieldName,
                    scopedCapability, result));
        }
    }

    private boolean scopeContains(JsonNode scope, StageCapability capability) {
        if (!scope.isArray()) return false;
        for (JsonNode item : scope) {
            if (item.isTextual() && capability.name().equals(item.asText())) return true;
        }
        return false;
    }

    private void validateSchemaShape(JsonNode schema, String path, List<String> errors, Set<JsonNode> visited) {
        if (schema == null || !schema.isObject() || !visited.add(schema)) {
            if (schema == null || !schema.isObject()) errors.add(path + "必须是Schema对象");
            return;
        }
        JsonNode ref = schema.get("$ref");
        if (ref != null && (!ref.isTextual() || !isLocalPointer(ref.asText()))) errors.add(path + "禁止远程$ref");
        if (schema.has("$dynamicRef") || schema.has("$recursiveRef")) errors.add(path + "禁止动态或递归引用");
        JsonNode type = schema.get("type");
        if (type != null && (!type.isTextual() || !SUPPORTED_TYPES.contains(type.asText()))) {
            errors.add(path + ".type不受支持");
        }
        JsonNode properties = schema.get("properties");
        if (properties != null) {
            if (!properties.isObject()) errors.add(path + ".properties必须是对象");
            else properties.fields().forEachRemaining(entry -> {
                if (EXECUTABLE_FIELDS.contains(normalize(entry.getKey()))) {
                    errors.add(path + ".properties禁止动态代码字段: " + entry.getKey());
                }
                validateSchemaShape(entry.getValue(), path + ".properties." + entry.getKey(), errors, visited);
            });
        }
        if (schema.has("items")) validateSchemaShape(schema.get("items"), path + ".items", errors, visited);
        for (String definitionsField : List.of("$defs", "definitions")) {
            JsonNode definitions = schema.get(definitionsField);
            if (definitions != null && definitions.isObject()) {
                definitions.fields().forEachRemaining(entry -> validateSchemaShape(
                        entry.getValue(), path + "." + definitionsField + "." + entry.getKey(), errors, visited));
            }
        }
    }

    private void validateValue(JsonNode root, JsonNode schema, JsonNode value, String path,
                               String fieldName, Predicate<String> secretExists, List<String> errors) {
        if (schema == null || !schema.isObject()) return;
        JsonNode reference = schema.get("$ref");
        if (reference != null && reference.isTextual() && isLocalPointer(reference.asText())) {
            validateValue(root, resolve(root, reference.asText()), value, path, fieldName, secretExists, errors);
            return;
        }
        boolean secretRefField = schema.path("x-secret-ref").asBoolean(false);
        boolean sensitive = schema.path("x-sensitive").asBoolean(false) || isSensitiveName(fieldName);
        if (secretRefField) {
            String ref = textualSecretReference(value);
            if (ref == null) errors.add(path + "必须是非空密钥引用");
            else if (!secretExists.test(ref)) errors.add(path + "引用的密钥不存在或不属于当前厂商");
            return;
        }
        if (sensitive) {
            validateSecretObject(value, path, secretExists, errors);
            return;
        }
        if (value == null || value.isMissingNode()) value = com.fasterxml.jackson.databind.node.NullNode.instance;
        String type = schema.path("type").asText(null);
        if (type != null && !matches(type, value)) {
            errors.add(path + "类型必须为" + type);
            return;
        }
        JsonNode enumValues = schema.get("enum");
        if (enumValues != null && enumValues.isArray()) {
            boolean matched = false;
            for (JsonNode candidate : enumValues) if (candidate.equals(value)) matched = true;
            if (!matched) errors.add(path + "不在允许的枚举值中");
        }
        if (value.isObject()) validateObject(root, schema, value, path, secretExists, errors);
        if (value.isArray()) validateArray(root, schema, value, path, secretExists, errors);
        if (value.isTextual()) validateString(schema, value.asText(), path, errors);
        if (value.isNumber()) validateNumber(schema, value.decimalValue(), path, errors);
    }

    private void validateObject(JsonNode root, JsonNode schema, JsonNode value, String path,
                                Predicate<String> secretExists, List<String> errors) {
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) required.forEach(field -> {
            if (!field.isTextual() || !value.has(field.asText()) || value.get(field.asText()).isNull()) {
                errors.add(path + "." + field.asText() + "为必填项");
            }
        });
        JsonNode properties = schema.path("properties");
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode propertySchema = properties.get(field.getKey());
            if (propertySchema != null) {
                validateValue(root, propertySchema, field.getValue(), path + "." + field.getKey(),
                        field.getKey(), secretExists, errors);
            } else if (isSensitiveName(field.getKey())) {
                validateSecretObject(field.getValue(), path + "." + field.getKey(), secretExists, errors);
            } else if (schema.has("additionalProperties")
                    && !schema.path("additionalProperties").asBoolean(true)) {
                errors.add(path + "包含未声明字段: " + field.getKey());
            }
        }
    }

    private void validateArray(JsonNode root, JsonNode schema, JsonNode value, String path,
                               Predicate<String> secretExists, List<String> errors) {
        JsonNode items = schema.get("items");
        if (items == null) return;
        for (int index = 0; index < value.size(); index++) {
            validateValue(root, items, value.get(index), path + "[" + index + "]", null, secretExists, errors);
        }
    }

    private void validateSecretObject(JsonNode value, String path, Predicate<String> secretExists,
                                      List<String> errors) {
        String ref = textualSecretReference(value);
        if (ref == null || !value.isObject() || value.size() != 1) {
            errors.add(path + "是敏感字段，只能保存{secretRef}");
        } else if (!secretExists.test(ref)) {
            errors.add(path + "引用的密钥不存在或不属于当前厂商");
        }
    }

    private String textualSecretReference(JsonNode value) {
        if (value != null && value.isTextual() && !value.asText().isBlank()) return value.asText();
        if (value != null && value.isObject() && value.size() == 1
                && value.path("secretRef").isTextual() && !value.path("secretRef").asText().isBlank()) {
            return value.path("secretRef").asText();
        }
        return null;
    }

    private void validateString(JsonNode schema, String value, String path, List<String> errors) {
        if (schema.has("minLength") && value.length() < schema.path("minLength").asInt()) errors.add(path + "长度小于minLength");
        if (schema.has("maxLength") && value.length() > schema.path("maxLength").asInt()) errors.add(path + "长度超过maxLength");
        if (schema.has("pattern")) try {
            if (!Pattern.compile(schema.path("pattern").asText()).matcher(value).matches()) errors.add(path + "不符合pattern");
        } catch (PatternSyntaxException exception) {
            errors.add(path + "对应的Schema pattern无效");
        }
    }

    private void validateNumber(JsonNode schema, BigDecimal value, String path, List<String> errors) {
        if (schema.has("minimum") && value.compareTo(schema.path("minimum").decimalValue()) < 0) errors.add(path + "小于minimum");
        if (schema.has("maximum") && value.compareTo(schema.path("maximum").decimalValue()) > 0) errors.add(path + "大于maximum");
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
        if (root == null || !root.isObject()) return true;
        Set<JsonNode> visiting = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<JsonNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int before = errors.size();
        visit(root, root, "$schema", visiting, visited, errors);
        return errors.size() == before;
    }

    private void visit(JsonNode root, JsonNode node, String path, Set<JsonNode> visiting,
                       Set<JsonNode> visited, List<String> errors) {
        if (node == null || node.isValueNode() || visited.contains(node)) return;
        if (!visiting.add(node)) { errors.add(path + "包含递归本地$ref"); return; }
        if (node.isObject()) {
            JsonNode reference = node.get("$ref");
            if (reference != null) {
                if (!reference.isTextual() || !isLocalPointer(reference.asText())) errors.add(path + "禁止远程或不受支持的$ref");
                else {
                    JsonNode target = resolve(root, reference.asText());
                    if (target == null) errors.add(path + "本地$ref无法解析");
                    else visit(root, target, path + ".$ref", visiting, visited, errors);
                }
            }
            node.fields().forEachRemaining(entry -> {
                if (!"$ref".equals(entry.getKey())) visit(root, entry.getValue(), path + "." + entry.getKey(), visiting, visited, errors);
            });
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) visit(root, node.get(index), path + "[" + index + "]", visiting, visited, errors);
        }
        visiting.remove(node);
        visited.add(node);
    }

    private boolean isSensitiveName(String fieldName) {
        if (fieldName == null || "secretRefs".equals(fieldName)) return false;
        return SENSITIVE_NAMES.contains(normalize(fieldName));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private boolean isLocalPointer(String reference) { return "#".equals(reference) || reference.startsWith("#/"); }
    private JsonNode resolve(JsonNode root, String reference) {
        JsonNode target = "#".equals(reference) ? root : root.at(reference.substring(1));
        return target == null || target.isMissingNode() ? null : target;
    }
}
