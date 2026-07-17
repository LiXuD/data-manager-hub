package com.dataplatform.access.call.service;

import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** 请求和响应共用的接口契约校验器。 */
public final class InterfaceContractValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$");

    private InterfaceContractValidator() {
    }

    public static ValidationResult validate(List<InterfaceParamDTO> fields,
                                            Map<String, Object> values,
                                            boolean applyDefaults) {
        Map<String, Object> safeValues = values != null ? values : new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        validateFields(fields != null ? fields : List.of(), safeValues, "", applyDefaults, errors);
        return new ValidationResult(errors.isEmpty(), errors);
    }

    @SuppressWarnings("unchecked")
    private static void validateFields(List<InterfaceParamDTO> fields, Map<String, Object> values,
                                       String parentPath, boolean applyDefaults, List<String> errors) {
        for (InterfaceParamDTO field : fields) {
            String name = field.getParamName();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String path = parentPath.isEmpty() ? name : parentPath + "." + name;
            Object value = values.get(name);
            if (isMissing(value) && applyDefaults && StringUtils.hasText(field.getDefaultValue())) {
                value = parseConfiguredValue(field.getDefaultValue(), field.getParamType());
                values.put(name, value);
            }
            if (isMissing(value)) {
                if (Boolean.TRUE.equals(field.getRequired())) {
                    errors.add(path + "不能为空");
                }
                continue;
            }
            String type = normalizeType(field.getParamType());
            if (!matchesType(value, type)) {
                errors.add(path + "类型必须为" + type);
                continue;
            }
            validateConstraints(path, value, field, errors);
            if ("object".equals(type) && value instanceof Map<?, ?> nested) {
                validateFields(field.getChildren(), (Map<String, Object>) nested, path, applyDefaults, errors);
            } else if ("array".equals(type) && value instanceof List<?> list) {
                String itemType = normalizeArrayItemType(field);
                for (int index = 0; index < list.size(); index++) {
                    Object item = list.get(index);
                    if (!StringUtils.hasText(itemType)) {
                        continue;
                    }
                    if (!matchesType(item, itemType)) {
                        errors.add(path + "[" + index + "]类型必须为" + itemType);
                        continue;
                    }
                    if ("object".equals(itemType) && item instanceof Map<?, ?> nested) {
                        validateFields(field.getChildren(), (Map<String, Object>) nested,
                                path + "[" + index + "]", applyDefaults, errors);
                    }
                }
            }
        }
    }

    private static void validateConstraints(String path, Object value, InterfaceParamDTO field,
                                            List<String> errors) {
        Map<String, JsonNode> constraints = parseConstraints(field.getConstraintConfig(), path, errors);
        applyLegacyRule(constraints, field.getValidationRule());
        JsonNode enumValues = constraints.get("enum");
        if (enumValues != null && enumValues.isArray()) {
            JsonNode actual = OBJECT_MAPPER.valueToTree(value);
            boolean matches = false;
            for (JsonNode allowed : enumValues) {
                if (allowed.equals(actual) || allowed.asText().equals(String.valueOf(value))) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                errors.add(path + "不在允许值范围内");
            }
        }
        if (value instanceof String text) {
            validateString(path, text, constraints, errors);
        }
        if (value instanceof Number number) {
            validateNumber(path, number.doubleValue(), constraints, errors);
        }
        if (value instanceof Collection<?> collection) {
            validateSize(path, collection.size(), constraints, "minItems", "maxItems", errors);
        }
    }

    private static void validateString(String path, String value, Map<String, JsonNode> constraints,
                                       List<String> errors) {
        validateSize(path, value.length(), constraints, "minLength", "maxLength", errors);
        JsonNode pattern = constraints.get("pattern");
        if (pattern != null) {
            try {
                if (!Pattern.matches(pattern.asText(), value)) {
                    errors.add(path + "格式不符合要求");
                }
            } catch (RuntimeException ex) {
                errors.add(path + "配置的正则表达式无效");
            }
        }
        JsonNode format = constraints.get("format");
        if (format != null && !matchesFormat(value, format.asText())) {
            errors.add(path + "不符合" + format.asText() + "格式");
        }
    }

    private static boolean matchesFormat(String value, String format) {
        try {
            return switch (format) {
                case "date" -> parseDate(value);
                case "date-time" -> parseDateTime(value);
                case "email" -> EMAIL_PATTERN.matcher(value).matches();
                case "uri" -> URI.create(value).isAbsolute();
                case "uuid" -> UUID.fromString(value).toString().equalsIgnoreCase(value);
                case "ipv4" -> matchesIpv4(value);
                case "ipv6" -> value.contains(":") && InetAddress.getByName(value).getHostAddress().contains(":");
                default -> false;
            };
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean parseDate(String value) {
        try {
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private static boolean parseDateTime(String value) {
        try {
            DateTimeFormatter.ISO_DATE_TIME.parse(value);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private static boolean matchesIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return false;
            }
            int number = Integer.parseInt(part);
            if (number > 255) {
                return false;
            }
        }
        return true;
    }

    private static void validateNumber(String path, double value, Map<String, JsonNode> constraints,
                                       List<String> errors) {
        JsonNode minimum = constraints.get("minimum");
        JsonNode maximum = constraints.get("maximum");
        if (minimum != null && value < minimum.asDouble()) {
            errors.add(path + "不能小于" + minimum.asText());
        }
        if (maximum != null && value > maximum.asDouble()) {
            errors.add(path + "不能大于" + maximum.asText());
        }
    }

    private static void validateSize(String path, int size, Map<String, JsonNode> constraints,
                                     String minKey, String maxKey, List<String> errors) {
        JsonNode minimum = constraints.get(minKey);
        JsonNode maximum = constraints.get(maxKey);
        if (minimum != null && size < minimum.asInt()) {
            errors.add(path + "长度不能小于" + minimum.asInt());
        }
        if (maximum != null && size > maximum.asInt()) {
            errors.add(path + "长度不能大于" + maximum.asInt());
        }
    }

    private static Map<String, JsonNode> parseConstraints(String raw, String path, List<String> errors) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(raw)) {
            return result;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            if (!node.isObject()) {
                errors.add(path + "的约束配置必须是JSON对象");
                return result;
            }
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue()));
        } catch (JsonProcessingException exception) {
            errors.add(path + "的约束配置不是有效JSON");
        }
        return result;
    }

    private static void applyLegacyRule(Map<String, JsonNode> constraints, String rule) {
        if (!StringUtils.hasText(rule)) {
            return;
        }
        if (rule.startsWith("regex:")) {
            constraints.putIfAbsent("pattern", OBJECT_MAPPER.valueToTree(rule.substring(6)));
        } else if (rule.startsWith("range:")) {
            String[] range = rule.substring(6).split("-", 2);
            if (range.length == 2) {
                try {
                    constraints.putIfAbsent("minimum", OBJECT_MAPPER.valueToTree(Double.parseDouble(range[0])));
                    constraints.putIfAbsent("maximum", OBJECT_MAPPER.valueToTree(Double.parseDouble(range[1])));
                } catch (NumberFormatException ignored) {
                    // 历史非法规则由契约配置页修复，不在调用链抛出系统异常。
                }
            }
        } else if ("not_empty".equals(rule)) {
            constraints.putIfAbsent("minLength", OBJECT_MAPPER.valueToTree(1));
        }
    }

    private static Object parseConfiguredValue(String raw, String type) {
        try {
            return switch (normalizeType(type)) {
                case "integer" -> Long.parseLong(raw);
                case "number" -> Double.parseDouble(raw);
                case "boolean" -> {
                    if (!"true".equalsIgnoreCase(raw) && !"false".equalsIgnoreCase(raw)) {
                        yield raw;
                    }
                    yield Boolean.parseBoolean(raw);
                }
                case "object", "array" -> OBJECT_MAPPER.readValue(raw, Object.class);
                default -> raw;
            };
        } catch (Exception ignored) {
            return raw;
        }
    }

    private static boolean isMissing(Object value) {
        return value == null || (value instanceof String text && text.trim().isEmpty());
    }

    private static boolean matchesType(Object value, String type) {
        return switch (type) {
            case "integer" -> value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map;
            case "array" -> value instanceof List;
            default -> value instanceof String;
        };
    }

    private static String normalizeType(String type) {
        return StringUtils.hasText(type) ? type.toLowerCase(Locale.ROOT) : "string";
    }

    private static String normalizeArrayItemType(InterfaceParamDTO field) {
        if (field.getChildren() != null && !field.getChildren().isEmpty()) {
            return "object";
        }
        return StringUtils.hasText(field.getArrayItemType()) ? normalizeType(field.getArrayItemType()) : null;
    }

    public record ValidationResult(boolean valid, List<String> errors) {
        public String firstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }
    }
}
