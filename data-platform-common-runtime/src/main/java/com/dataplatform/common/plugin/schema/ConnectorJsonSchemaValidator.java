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
    private static final Set<String> CONDITION_FIELDS = Set.of(
            "type", "const", "enum", "required", "properties", "allOf", "anyOf", "oneOf", "not");
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "password", "passwd", "token", "accesstoken", "refreshtoken", "secret",
            "clientsecret", "apisecret", "secretkey", "apitoken", "apikey", "privatekey",
            "signingkey", "encryptionkey", "certificate", "cert", "credential");
    private static final Pattern VISIBILITY_FIELD = Pattern.compile("^[A-Za-z_][A-Za-z0-9_-]{0,127}$");

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
                } else if (schema.get("additionalProperties") != null
                        && schema.get("additionalProperties").isObject()) {
                    collectSecretReferences(root, schema.get("additionalProperties"), entry.getValue(),
                            entry.getKey(), scopedCapability, result);
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
        validateSchemaConstraints(schema, path, errors);
        JsonNode required = schema.get("required");
        if (required != null && (!required.isArray() || !allTextual(required))) {
            errors.add(path + ".required必须是字符串数组");
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
        JsonNode additionalProperties = schema.get("additionalProperties");
        if (additionalProperties != null) {
            if (!additionalProperties.isBoolean() && !additionalProperties.isObject()) {
                errors.add(path + ".additionalProperties必须是布尔值或Schema对象");
            } else if (additionalProperties.isObject()) {
                validateSchemaShape(additionalProperties, path + ".additionalProperties", errors, visited);
            }
        }
        if (schema.has("items")) validateSchemaShape(schema.get("items"), path + ".items", errors, visited);
        validateVisibilityCondition(schema.get("x-ui-visible-if"), path, errors);
        validateConditionalSchema(schema, path, errors, visited);
        validateCombinatorSchemas(schema, path, errors, visited);
        for (String definitionsField : List.of("$defs", "definitions")) {
            JsonNode definitions = schema.get(definitionsField);
            if (definitions != null && definitions.isObject()) {
                definitions.fields().forEachRemaining(entry -> validateSchemaShape(
                        entry.getValue(), path + "." + definitionsField + "." + entry.getKey(), errors, visited));
            }
        }
    }

    private void validateSchemaConstraints(JsonNode schema, String path, List<String> errors) {
        JsonNode enumValues = schema.get("enum");
        if (enumValues != null && (!enumValues.isArray() || enumValues.isEmpty())) {
            errors.add(path + ".enum必须是非空数组");
        }
        validateNonNegativeInteger(schema.get("minLength"), path + ".minLength", errors);
        validateNonNegativeInteger(schema.get("maxLength"), path + ".maxLength", errors);
        validateNonNegativeInteger(schema.get("minItems"), path + ".minItems", errors);
        validateNonNegativeInteger(schema.get("maxItems"), path + ".maxItems", errors);
        validateNonNegativeInteger(schema.get("minProperties"), path + ".minProperties", errors);
        validateNonNegativeInteger(schema.get("maxProperties"), path + ".maxProperties", errors);
        JsonNode uniqueItems = schema.get("uniqueItems");
        if (uniqueItems != null && !uniqueItems.isBoolean()) {
            errors.add(path + ".uniqueItems必须是布尔值");
        }
        validateNumberKeyword(schema.get("minimum"), path + ".minimum", errors);
        validateNumberKeyword(schema.get("maximum"), path + ".maximum", errors);
        validateNumberKeyword(schema.get("exclusiveMinimum"), path + ".exclusiveMinimum", errors);
        validateNumberKeyword(schema.get("exclusiveMaximum"), path + ".exclusiveMaximum", errors);
        JsonNode pattern = schema.get("pattern");
        if (pattern != null) {
            if (!pattern.isTextual()) {
                errors.add(path + ".pattern必须是字符串");
            } else {
                try {
                    Pattern.compile(pattern.asText());
                } catch (PatternSyntaxException exception) {
                    errors.add(path + ".pattern对应的Schema pattern无效");
                }
            }
        }
    }

    private void validateNonNegativeInteger(JsonNode value, String path, List<String> errors) {
        if (value != null && (!value.isIntegralNumber() || value.asLong() < 0)) {
            errors.add(path + "必须是非负整数");
        }
    }

    private void validateNumberKeyword(JsonNode value, String path, List<String> errors) {
        if (value != null && !value.isNumber()) {
            errors.add(path + "必须是数字");
        }
    }

    private void validateVisibilityCondition(JsonNode condition, String path, List<String> errors) {
        if (condition != null && !isVisibilityConditionShapeValid(condition)) {
            errors.add(path + ".x-ui-visible-if不受支持");
        }
    }

    private boolean isVisibilityConditionShapeValid(JsonNode condition) {
        if (condition == null || !condition.isObject() || condition.isEmpty()) return false;
        boolean structured = condition.fieldNames().hasNext()
                && (condition.has("field") || condition.has("equals")
                || condition.has("notEquals") || condition.has("in") || condition.has("present"));
        if (!structured) {
            if (condition.size() != 1) return false;
            String field = condition.fieldNames().next();
            return isVisibilityField(field);
        }
        if (condition.fieldNames().hasNext()) {
            var fields = condition.fieldNames();
            while (fields.hasNext()) {
                String key = fields.next();
                if (!Set.of("field", "equals", "notEquals", "in", "present").contains(key)) return false;
            }
        }
        JsonNode field = condition.get("field");
        if (field == null || !field.isTextual() || !isVisibilityField(field.asText())) return false;
        if (!condition.has("equals") && !condition.has("notEquals")
                && !condition.has("in") && !condition.has("present")) return false;
        if (condition.has("in") && !condition.get("in").isArray()) return false;
        return !condition.has("present") || condition.get("present").isBoolean();
    }

    private boolean isVisibilityField(String field) {
        return field != null && VISIBILITY_FIELD.matcher(field).matches();
    }

    private void validateConditionalSchema(JsonNode schema, String path, List<String> errors,
                                           Set<JsonNode> visited) {
        JsonNode condition = schema.get("if");
        if (condition != null && !condition.isObject()) {
            errors.add(path + ".if必须是Schema对象");
        } else if (condition != null) {
            validateConditionShape(condition, path + ".if", errors);
            validateSchemaShape(condition, path + ".if", errors, visited);
        }
        for (String branch : List.of("then", "else")) {
            JsonNode branchSchema = schema.get(branch);
            if (branchSchema != null && !branchSchema.isObject()) {
                errors.add(path + "." + branch + "必须是Schema对象");
            } else if (branchSchema != null) {
                validateSchemaShape(branchSchema, path + "." + branch, errors, visited);
            }
        }
    }

    private void validateCombinatorSchemas(JsonNode schema, String path,
                                           List<String> errors, Set<JsonNode> visited) {
        for (String keyword : List.of("allOf", "anyOf", "oneOf")) {
            JsonNode branches = schema.get(keyword);
            if (branches == null) continue;
            if (!branches.isArray() || branches.isEmpty()) {
                errors.add(path + "." + keyword + "必须是非空Schema数组");
                continue;
            }
            for (int index = 0; index < branches.size(); index++) {
                validateSchemaShape(branches.get(index), path + "." + keyword + "[" + index + "]",
                        errors, visited);
            }
        }
        JsonNode not = schema.get("not");
        if (not != null) validateSchemaShape(not, path + ".not", errors, visited);
    }

    private void validateConditionShape(JsonNode condition, String path, List<String> errors) {
        if (condition == null || !condition.isObject()) {
            errors.add(path + "必须是Schema对象");
            return;
        }
        var fields = condition.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!CONDITION_FIELDS.contains(field)) {
                errors.add(path + "包含不受支持的条件字段: " + field);
            }
        }
        JsonNode type = condition.get("type");
        if (type != null && (!type.isTextual() || !SUPPORTED_TYPES.contains(type.asText()))) {
            errors.add(path + ".type不受支持");
        }
        JsonNode enumValues = condition.get("enum");
        if (enumValues != null && (!enumValues.isArray() || enumValues.isEmpty())) {
            errors.add(path + ".enum必须是非空数组");
        }
        JsonNode required = condition.get("required");
        if (required != null && (!required.isArray()
                || !allTextual(required))) {
            errors.add(path + ".required必须是字符串数组");
        }
        JsonNode properties = condition.get("properties");
        if (properties != null) {
            if (!properties.isObject()) {
                errors.add(path + ".properties必须是对象");
            } else {
                properties.fields().forEachRemaining(entry -> {
                    if (!entry.getValue().isObject()) {
                        errors.add(path + ".properties." + entry.getKey() + "必须是Schema对象");
                    } else {
                        validateConditionShape(entry.getValue(), path + ".properties." + entry.getKey(), errors);
                    }
                });
            }
        }
        for (String keyword : List.of("allOf", "anyOf", "oneOf")) {
            JsonNode branches = condition.get(keyword);
            if (branches != null && branches.isArray()) {
                for (int index = 0; index < branches.size(); index++) {
                    validateConditionShape(branches.get(index), path + "." + keyword + "[" + index + "]", errors);
                }
            }
        }
        JsonNode not = condition.get("not");
        if (not != null) validateConditionShape(not, path + ".not", errors);
    }

    private boolean allTextual(JsonNode values) {
        for (JsonNode value : values) if (!value.isTextual()) return false;
        return true;
    }

    private void validateValue(JsonNode root, JsonNode schema, JsonNode value, String path,
                               String fieldName, Predicate<String> secretExists, List<String> errors) {
        if (schema == null || !schema.isObject()) return;
        JsonNode reference = schema.get("$ref");
        if (reference != null && reference.isTextual() && isLocalPointer(reference.asText())) {
            validateValue(root, resolve(root, reference.asText()), value, path, fieldName, secretExists, errors);
            return;
        }
        if (value == null || value.isMissingNode()) value = com.fasterxml.jackson.databind.node.NullNode.instance;
        boolean secretRefField = schema.path("x-secret-ref").asBoolean(false);
        boolean sensitive = schema.path("x-sensitive").asBoolean(false) || isSensitiveName(fieldName);
        if (sensitive && !secretRefField) {
            validateSecretObject(root, schema, value, path, secretExists, errors);
            return;
        }
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
        if (schema.has("const") && !jsonEquals(value, schema.get("const"))) {
            errors.add(path + "不符合const约束");
        }
        if (secretRefField) {
            String ref = textualSecretReference(value);
            if (ref == null) {
                errors.add(path + "必须是非空密钥引用");
            } else {
                if (value.isTextual()) validateString(schema, value.asText(), path, errors);
                if (value.isObject()) {
                    validateObjectSize(schema, value, path, errors);
                    validateSecretReferenceProperty(root, schema, value, path, secretExists, errors);
                }
                if (!secretExists.test(ref)) {
                    errors.add(path + "引用的密钥不存在或不属于当前厂商");
                }
            }
            return;
        }
        if (value.isObject()) {
            validateObject(root, schema, value, path, secretExists, errors);
            validateConditionalBranch(root, schema, value, path, fieldName, secretExists, errors);
        }
        validateCombinators(root, schema, value, path, fieldName, secretExists, errors);
        if (value.isArray()) validateArray(root, schema, value, path, secretExists, errors);
        if (value.isTextual()) validateString(schema, value.asText(), path, errors);
        if (value.isNumber()) validateNumber(schema, value.decimalValue(), path, errors);
    }

    private void validateObject(JsonNode root, JsonNode schema, JsonNode value, String path,
                                Predicate<String> secretExists, List<String> errors) {
        validateObjectSize(schema, value, path, errors);
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) required.forEach(field -> {
            JsonNode fieldSchema = field.isTextual() ? schema.path("properties").get(field.asText()) : null;
            if (field.isTextual() && !isVisible(fieldSchema, value)) return;
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
                if (!isVisible(propertySchema, value)) {
                    errors.add(path + "." + field.getKey() + "在当前条件下不可见，不应提交");
                    continue;
                }
                validateValue(root, propertySchema, field.getValue(), path + "." + field.getKey(),
                        field.getKey(), secretExists, errors);
            } else if (isSensitiveName(field.getKey())) {
                validateSecretObject(root, null, field.getValue(), path + "." + field.getKey(), secretExists, errors);
            } else if (schema.has("additionalProperties")) {
                JsonNode additionalProperties = schema.get("additionalProperties");
                if (additionalProperties.isBoolean() && !additionalProperties.asBoolean()) {
                    errors.add(path + "包含未声明字段: " + field.getKey());
                } else if (additionalProperties.isObject()) {
                    validateValue(root, additionalProperties, field.getValue(), path + "." + field.getKey(),
                            field.getKey(), secretExists, errors);
                }
            }
        }
    }

    private void validateConditionalBranch(JsonNode root, JsonNode schema, JsonNode value,
                                            String path, String fieldName,
                                            Predicate<String> secretExists, List<String> errors) {
        JsonNode condition = schema.get("if");
        if (condition == null || !condition.isObject()) return;
        JsonNode branch = conditionMatches(condition, value) ? schema.get("then") : schema.get("else");
        if (branch != null) {
            validateValue(root, branch, value, path, fieldName, secretExists, errors);
        }
    }

    private void validateCombinators(JsonNode root, JsonNode schema, JsonNode value,
                                     String path, String fieldName,
                                     Predicate<String> secretExists, List<String> errors) {
        JsonNode allOf = schema.get("allOf");
        if (allOf != null && allOf.isArray()) {
            for (JsonNode branch : allOf) {
                validateValue(root, branch, value, path, fieldName, secretExists, errors);
            }
        }

        validateAnyOf(root, schema.get("anyOf"), value, path, fieldName, secretExists, errors, false);
        validateAnyOf(root, schema.get("oneOf"), value, path, fieldName, secretExists, errors, true);

        JsonNode not = schema.get("not");
        if (not != null && not.isObject()) {
            List<String> branchErrors = new ArrayList<>();
            validateValue(root, not, value, path, fieldName, secretExists, branchErrors);
            if (branchErrors.isEmpty()) errors.add(path + "不应匹配not条件");
        }
    }

    private void validateAnyOf(JsonNode root, JsonNode branches, JsonNode value,
                               String path, String fieldName,
                               Predicate<String> secretExists, List<String> errors,
                               boolean exactlyOne) {
        if (branches == null || !branches.isArray()) return;
        int matched = 0;
        for (JsonNode branch : branches) {
            List<String> branchErrors = new ArrayList<>();
            validateValue(root, branch, value, path, fieldName, secretExists, branchErrors);
            if (branchErrors.isEmpty()) matched++;
        }
        if ((exactlyOne && matched != 1) || (!exactlyOne && matched == 0)) {
            errors.add(path + (exactlyOne ? "不匹配oneOf条件" : "不匹配anyOf条件"));
        }
    }

    private boolean conditionMatches(JsonNode condition, JsonNode value) {
        if (condition == null || !condition.isObject() || value == null) return false;
        JsonNode type = condition.get("type");
        if (type != null && (!type.isTextual() || !matches(type.asText(), value))) return false;
        if (condition.has("const") && !jsonEquals(value, condition.get("const"))) return false;
        JsonNode enumValues = condition.get("enum");
        if (enumValues != null && enumValues.isArray() && !containsJson(enumValues, value)) return false;
        JsonNode allOf = condition.get("allOf");
        if (allOf != null && allOf.isArray()) {
            for (JsonNode branch : allOf) {
                if (!conditionMatches(branch, value)) return false;
            }
        }
        JsonNode anyOf = condition.get("anyOf");
        if (anyOf != null && anyOf.isArray()) {
            boolean matched = false;
            for (JsonNode branch : anyOf) {
                if (conditionMatches(branch, value)) matched = true;
            }
            if (!matched) return false;
        }
        JsonNode oneOf = condition.get("oneOf");
        if (oneOf != null && oneOf.isArray()) {
            int matched = 0;
            for (JsonNode branch : oneOf) {
                if (conditionMatches(branch, value)) matched++;
            }
            if (matched != 1) return false;
        }
        JsonNode not = condition.get("not");
        if (not != null && not.isObject() && conditionMatches(not, value)) return false;
        JsonNode required = condition.get("required");
        if (required != null && required.isArray()) {
            if (!value.isObject()) return false;
            for (JsonNode field : required) {
                if (!field.isTextual() || !value.has(field.asText()) || value.get(field.asText()).isNull()) {
                    return false;
                }
            }
        }
        JsonNode properties = condition.get("properties");
        if (properties != null && properties.isObject()) {
            if (!value.isObject()) return false;
            var fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode actual = value.get(entry.getKey());
                JsonNode expected = entry.getValue();
                if (actual == null || !conditionMatches(expected, actual)) return false;
            }
        }
        return true;
    }

    private boolean containsJson(JsonNode values, JsonNode actual) {
        for (JsonNode value : values) if (jsonEquals(value, actual)) return true;
        return false;
    }

    private boolean jsonEquals(JsonNode left, JsonNode right) {
        return left != null && right != null && !left.isMissingNode() && left.equals(right);
    }

    private boolean isVisible(JsonNode schema, JsonNode parent) {
        JsonNode condition = schema == null ? null : schema.get("x-ui-visible-if");
        if (condition == null) return true;
        if (!isVisibilityConditionShapeValid(condition)
                || parent == null || !parent.isObject()) return false;
        if (condition.has("field")) {
            String field = condition.get("field").asText();
            JsonNode actual = parent.get(field);
            if (condition.has("present")) {
                boolean present = actual != null && !actual.isNull() && !actual.isMissingNode();
                if (present != condition.get("present").asBoolean()) return false;
            }
            if (condition.has("equals") && !jsonEquals(actual, condition.get("equals"))) return false;
            if (condition.has("notEquals") && jsonEquals(actual, condition.get("notEquals"))) return false;
            if (condition.has("in") && !containsJson(condition.get("in"), actual)) return false;
            return true;
        }
        var fields = condition.fields();
        Map.Entry<String, JsonNode> entry = fields.next();
        return jsonEquals(parent.get(entry.getKey()), entry.getValue());
    }

    private void validateArray(JsonNode root, JsonNode schema, JsonNode value, String path,
                               Predicate<String> secretExists, List<String> errors) {
        JsonNode minItems = schema.get("minItems");
        if (minItems != null && minItems.isIntegralNumber() && value.size() < minItems.asLong()) {
            errors.add(path + "元素数量小于minItems");
        }
        JsonNode maxItems = schema.get("maxItems");
        if (maxItems != null && maxItems.isIntegralNumber() && value.size() > maxItems.asLong()) {
            errors.add(path + "元素数量超过maxItems");
        }
        if (schema.path("uniqueItems").asBoolean(false)) {
            Set<JsonNode> uniqueItems = new HashSet<>();
            for (JsonNode item : value) {
                if (!uniqueItems.add(item)) {
                    errors.add(path + "包含重复元素");
                    break;
                }
            }
        }
        JsonNode items = schema.get("items");
        if (items == null) return;
        for (int index = 0; index < value.size(); index++) {
            validateValue(root, items, value.get(index), path + "[" + index + "]", null, secretExists, errors);
        }
    }

    private void validateSecretObject(JsonNode root, JsonNode schema, JsonNode value, String path,
                                      Predicate<String> secretExists, List<String> errors) {
        String ref = textualSecretReference(value);
        if (ref == null || !value.isObject() || value.size() != 1) {
            errors.add(path + "是敏感字段，只能保存{secretRef}");
            return;
        }
        if (schema != null) {
            validateObjectSize(schema, value, path, errors);
            validateSecretReferenceProperty(root, schema, value, path, secretExists, errors);
        }
        if (!secretExists.test(ref)) {
            errors.add(path + "引用的密钥不存在或不属于当前厂商");
        }
    }

    private void validateSecretReferenceProperty(JsonNode root, JsonNode schema, JsonNode value, String path,
                                                  Predicate<String> secretExists, List<String> errors) {
        JsonNode properties = schema == null ? null : schema.get("properties");
        JsonNode referenceSchema = properties == null || !properties.isObject()
                ? null : properties.get("secretRef");
        if (referenceSchema != null) {
            validateValue(root, referenceSchema, value.get("secretRef"), path + ".secretRef",
                    "secretRef", secretExists, errors);
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
        int length = value.codePointCount(0, value.length());
        JsonNode minLength = schema.get("minLength");
        if (minLength != null && minLength.isIntegralNumber() && length < minLength.asLong()) {
            errors.add(path + "长度小于minLength");
        }
        JsonNode maxLength = schema.get("maxLength");
        if (maxLength != null && maxLength.isIntegralNumber() && length > maxLength.asLong()) {
            errors.add(path + "长度超过maxLength");
        }
        JsonNode pattern = schema.get("pattern");
        if (pattern != null && pattern.isTextual()) {
            try {
                if (!Pattern.compile(pattern.asText()).matcher(value).find()) errors.add(path + "不符合pattern");
            } catch (PatternSyntaxException exception) {
                errors.add(path + "对应的Schema pattern无效");
            }
        }
    }

    private void validateNumber(JsonNode schema, BigDecimal value, String path, List<String> errors) {
        JsonNode minimum = schema.get("minimum");
        if (minimum != null && minimum.isNumber() && value.compareTo(minimum.decimalValue()) < 0) {
            errors.add(path + "小于minimum");
        }
        JsonNode maximum = schema.get("maximum");
        if (maximum != null && maximum.isNumber() && value.compareTo(maximum.decimalValue()) > 0) {
            errors.add(path + "大于maximum");
        }
        JsonNode exclusiveMinimum = schema.get("exclusiveMinimum");
        if (exclusiveMinimum != null && exclusiveMinimum.isNumber()
                && value.compareTo(exclusiveMinimum.decimalValue()) <= 0) {
            errors.add(path + "不满足exclusiveMinimum");
        }
        JsonNode exclusiveMaximum = schema.get("exclusiveMaximum");
        if (exclusiveMaximum != null && exclusiveMaximum.isNumber()
                && value.compareTo(exclusiveMaximum.decimalValue()) >= 0) {
            errors.add(path + "不满足exclusiveMaximum");
        }
    }

    private void validateObjectSize(JsonNode schema, JsonNode value, String path, List<String> errors) {
        JsonNode minProperties = schema.get("minProperties");
        if (minProperties != null && minProperties.isIntegralNumber()
                && value.size() < minProperties.asLong()) {
            errors.add(path + "属性数量小于minProperties");
        }
        JsonNode maxProperties = schema.get("maxProperties");
        if (maxProperties != null && maxProperties.isIntegralNumber()
                && value.size() > maxProperties.asLong()) {
            errors.add(path + "属性数量超过maxProperties");
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
        try {
            JsonNode target = "#".equals(reference) ? root : root.at(reference.substring(1));
            return target == null || target.isMissingNode() ? null : target;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
