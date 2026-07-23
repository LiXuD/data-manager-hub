package com.dataplatform.masterdata.interface_.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.entity.InterfaceParam;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.interface_.service.InterfaceContractService;
import com.dataplatform.masterdata.interface_.service.InterfaceParamService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InterfaceContractServiceImpl implements InterfaceContractService {

    private static final String REQUEST = "REQUEST";
    private static final String RESPONSE = "RESPONSE";
    private static final int MAX_TREE_DEPTH = 20;
    private static final int MAX_FIELD_COUNT = 1000;
    private static final Pattern FIELD_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_-]{0,63}$");
    private static final Set<String> TYPES = Set.of("string", "integer", "number", "boolean", "object", "array");
    private static final Set<String> ARRAY_ITEM_TYPES = Set.of("string", "integer", "number", "boolean", "object");
    private static final Set<String> CONSTRAINT_KEYS = Set.of(
            "enum", "pattern", "minimum", "maximum", "minLength", "maxLength",
            "minItems", "maxItems", "format");
    private static final Set<String> STRING_CONSTRAINT_KEYS = Set.of(
            "enum", "pattern", "minLength", "maxLength", "format");
    private static final Set<String> NUMBER_CONSTRAINT_KEYS = Set.of(
            "enum", "minimum", "maximum");
    private static final Set<String> ARRAY_CONSTRAINT_KEYS = Set.of(
            "enum", "minItems", "maxItems");
    private static final Set<String> SCALAR_CONSTRAINT_KEYS = Set.of("enum");
    private static final Set<String> SUPPORTED_FORMATS = Set.of(
            "date", "date-time", "email", "uri", "uuid", "ipv4", "ipv6");

    private final ApiInterfaceService apiInterfaceService;
    private final InterfaceParamService interfaceParamService;
    private final ObjectMapper objectMapper;

    public InterfaceContractServiceImpl(ApiInterfaceService apiInterfaceService,
                                        InterfaceParamService interfaceParamService,
                                        ObjectMapper objectMapper) {
        this.apiInterfaceService = apiInterfaceService;
        this.interfaceParamService = interfaceParamService;
        this.objectMapper = objectMapper;
    }

    @Override
    public InterfaceContractDTO getContract(Long interfaceId) {
        ApiInterface apiInterface = requireInterface(interfaceId);
        List<InterfaceParam> fields = interfaceParamService.listByInterfaceId(interfaceId);
        InterfaceContractDTO dto = baseContract(apiInterface);
        dto.setRequestFields(buildTree(fields, REQUEST));
        dto.setResponseFields(buildTree(fields, RESPONSE));
        dto.setRequestSchema(writeSchema(dto.getRequestFields()));
        dto.setResponseSchema(writeSchema(dto.getResponseFields()));
        return dto;
    }

    @Override
    @Transactional
    public InterfaceContractDTO saveContract(Long interfaceId, InterfaceContractDTO contract) {
        ApiInterface apiInterface = requireInterface(interfaceId);
        List<InterfaceParamDTO> requestFields = contract != null && contract.getRequestFields() != null
                ? contract.getRequestFields() : List.of();
        List<InterfaceParamDTO> responseFields = contract != null && contract.getResponseFields() != null
                ? contract.getResponseFields() : List.of();
        validateTree(requestFields, "requestFields");
        validateTree(responseFields, "responseFields");

        interfaceParamService.remove(new LambdaQueryWrapper<InterfaceParam>()
                .eq(InterfaceParam::getInterfaceId, interfaceId));
        persistTree(interfaceId, null, REQUEST, requestFields);
        persistTree(interfaceId, null, RESPONSE, responseFields);

        String requestSchema = writeSchema(requestFields);
        String responseSchema = writeSchema(responseFields);
        if (!apiInterfaceService.updateSchema(interfaceId, requestSchema, responseSchema)) {
            throw new IllegalStateException("接口契约快照更新失败");
        }
        apiInterface.setRequestSchema(requestSchema);
        apiInterface.setResponseSchema(responseSchema);
        return getContract(interfaceId);
    }

    @Override
    @Transactional
    public InterfaceContractDTO refreshSnapshots(Long interfaceId) {
        InterfaceContractDTO contract = getContract(interfaceId);
        String requestSchema = writeSchema(contract.getRequestFields());
        String responseSchema = writeSchema(contract.getResponseFields());
        if (!apiInterfaceService.updateSchema(interfaceId, requestSchema, responseSchema)) {
            throw new IllegalStateException("接口契约快照更新失败");
        }
        contract.setRequestSchema(requestSchema);
        contract.setResponseSchema(responseSchema);
        return contract;
    }

    private ApiInterface requireInterface(Long interfaceId) {
        ApiInterface apiInterface = apiInterfaceService.getById(interfaceId);
        if (apiInterface == null) {
            throw new IllegalArgumentException("接口不存在");
        }
        return apiInterface;
    }

    private InterfaceContractDTO baseContract(ApiInterface entity) {
        InterfaceContractDTO dto = new InterfaceContractDTO();
        dto.setInterfaceId(entity.getId());
        dto.setInterfaceCode(entity.getInterfaceCode());
        dto.setInterfaceName(entity.getInterfaceName());
        dto.setDescription(entity.getDescription());
        dto.setRequestSchema(entity.getRequestSchema());
        dto.setResponseSchema(entity.getResponseSchema());
        dto.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt() : LocalDateTime.now());
        return dto;
    }

    private List<InterfaceParamDTO> buildTree(List<InterfaceParam> entities, String direction) {
        Map<Long, InterfaceParamDTO> byId = new HashMap<>();
        List<InterfaceParam> selected = entities.stream()
                .filter(item -> direction.equalsIgnoreCase(defaultDirection(item.getDirection())))
                .sorted(Comparator.comparing(item -> item.getSort() != null ? item.getSort() : 0))
                .toList();
        for (InterfaceParam entity : selected) {
            InterfaceParamDTO dto = toDTO(entity);
            dto.setChildren(new ArrayList<>());
            byId.put(entity.getId(), dto);
        }
        List<InterfaceParamDTO> roots = new ArrayList<>();
        for (InterfaceParam entity : selected) {
            InterfaceParamDTO dto = byId.get(entity.getId());
            InterfaceParamDTO parent = entity.getParentId() != null ? byId.get(entity.getParentId()) : null;
            if (parent == null) {
                roots.add(dto);
            } else {
                parent.getChildren().add(dto);
            }
        }
        return roots;
    }

    private InterfaceParamDTO toDTO(InterfaceParam entity) {
        InterfaceParamDTO dto = new InterfaceParamDTO();
        dto.setId(entity.getId());
        dto.setInterfaceId(entity.getInterfaceId());
        dto.setDirection(defaultDirection(entity.getDirection()));
        dto.setParentId(entity.getParentId());
        dto.setParamName(entity.getParamName());
        dto.setDescription(entity.getDescription());
        dto.setParamType(entity.getParamType());
        dto.setArrayItemType(entity.getArrayItemType());
        dto.setRequired(entity.getRequired());
        dto.setDefaultValue(entity.getDefaultValue());
        dto.setExampleValue(entity.getExampleValue());
        dto.setConstraintConfig(entity.getConstraintConfig());
        dto.setSort(entity.getSort());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private void persistTree(Long interfaceId, Long parentId, String direction, List<InterfaceParamDTO> fields) {
        for (int index = 0; index < fields.size(); index++) {
            InterfaceParamDTO dto = fields.get(index);
            InterfaceParam entity = new InterfaceParam();
            entity.setInterfaceId(interfaceId);
            entity.setDirection(direction);
            entity.setParentId(parentId);
            entity.setParamName(dto.getParamName().trim());
            entity.setDescription(dto.getDescription());
            entity.setParamType(normalizeType(dto.getParamType()));
            entity.setArrayItemType("array".equals(entity.getParamType())
                    ? normalizeArrayItemType(dto.getArrayItemType(), dto.getChildren()) : null);
            entity.setRequired(Boolean.TRUE.equals(dto.getRequired()));
            entity.setDefaultValue(dto.getDefaultValue());
            entity.setExampleValue(dto.getExampleValue());
            entity.setConstraintConfig(normalizeConstraints(dto.getConstraintConfig()));
            entity.setSort(dto.getSort() != null ? dto.getSort() : index);
            if (!interfaceParamService.save(entity) || entity.getId() == null) {
                throw new IllegalStateException("接口契约字段保存失败: " + entity.getParamName());
            }
            persistTree(interfaceId, entity.getId(), direction, dto.getChildren());
        }
    }

    private void validateTree(List<InterfaceParamDTO> fields, String path) {
        validateTree(fields, path, 0, new int[]{0});
    }

    private void validateTree(List<InterfaceParamDTO> fields, String path, int depth, int[] fieldCount) {
        if (depth > MAX_TREE_DEPTH) {
            throw new IllegalArgumentException(path + "嵌套层级不能超过" + MAX_TREE_DEPTH);
        }
        Set<String> siblingNames = new HashSet<>();
        for (int index = 0; index < fields.size(); index++) {
            if (++fieldCount[0] > MAX_FIELD_COUNT) {
                throw new IllegalArgumentException("接口契约字段总数不能超过" + MAX_FIELD_COUNT);
            }
            InterfaceParamDTO field = fields.get(index);
            String fieldPath = path + "[" + index + "]";
            if (field == null || !StringUtils.hasText(field.getParamName())) {
                throw new IllegalArgumentException(fieldPath + ".paramName不能为空");
            }
            String name = field.getParamName().trim();
            if (!FIELD_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException(fieldPath + ".paramName格式无效");
            }
            if (!siblingNames.add(name)) {
                throw new IllegalArgumentException(path + "存在重复字段: " + name);
            }
            String type = normalizeType(field.getParamType());
            if (!TYPES.contains(type)) {
                throw new IllegalArgumentException(fieldPath + ".paramType不支持: " + field.getParamType());
            }
            if (!field.getChildren().isEmpty() && !"object".equals(type) && !"array".equals(type)) {
                throw new IllegalArgumentException(fieldPath + "只有object或array可以包含子字段");
            }
            if ("array".equals(type)) {
                String itemType = normalizeArrayItemType(field.getArrayItemType(), field.getChildren());
                if (itemType != null && !ARRAY_ITEM_TYPES.contains(itemType)) {
                    throw new IllegalArgumentException(fieldPath + ".arrayItemType不支持: " + field.getArrayItemType());
                }
                if (!field.getChildren().isEmpty() && !"object".equals(itemType)) {
                    throw new IllegalArgumentException(fieldPath + "包含子字段时arrayItemType必须为object");
                }
                field.setArrayItemType(itemType);
            } else if (StringUtils.hasText(field.getArrayItemType())) {
                throw new IllegalArgumentException(fieldPath + ".arrayItemType仅适用于array类型");
            }
            validateConstraints(field.getConstraintConfig(), type, fieldPath + ".constraintConfig");
            validateConfiguredValue(field.getDefaultValue(), type, field.getConstraintConfig(), fieldPath + ".defaultValue");
            validateConfiguredValue(field.getExampleValue(), type, field.getConstraintConfig(), fieldPath + ".exampleValue");
            if ("array".equals(type)) {
                validateArrayItems(field.getDefaultValue(), field.getArrayItemType(), fieldPath + ".defaultValue");
                validateArrayItems(field.getExampleValue(), field.getArrayItemType(), fieldPath + ".exampleValue");
            }
            validateTree(field.getChildren(), fieldPath + ".children", depth + 1, fieldCount);
        }
    }

    private void validateConstraints(String raw, String type, String path) {
        if (!StringUtils.hasText(raw)) {
            return;
        }
        JsonNode constraints;
        try {
            constraints = objectMapper.readTree(raw);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(path + "必须是有效JSON", exception);
        }
        if (!constraints.isObject()) {
            throw new IllegalArgumentException(path + "必须是JSON对象");
        }
        Set<String> applicable = switch (type) {
            case "string" -> STRING_CONSTRAINT_KEYS;
            case "integer", "number" -> NUMBER_CONSTRAINT_KEYS;
            case "array" -> ARRAY_CONSTRAINT_KEYS;
            default -> SCALAR_CONSTRAINT_KEYS;
        };
        constraints.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (!CONSTRAINT_KEYS.contains(key)) {
                throw new IllegalArgumentException("不支持的字段约束: " + key);
            }
            if (!applicable.contains(key)) {
                throw new IllegalArgumentException(path + "." + key + "不适用于" + type + "类型");
            }
            switch (key) {
                case "enum" -> validateEnum(value, type, path + ".enum");
                case "pattern" -> {
                    if (!value.isTextual()) {
                        throw new IllegalArgumentException(path + ".pattern必须是字符串");
                    }
                    try {
                        Pattern.compile(value.asText());
                    } catch (RuntimeException exception) {
                        throw new IllegalArgumentException(path + ".pattern不是有效正则表达式", exception);
                    }
                }
                case "minimum", "maximum" -> {
                    if (!value.isNumber()) {
                        throw new IllegalArgumentException(path + "." + key + "必须是数字");
                    }
                }
                case "minLength", "maxLength", "minItems", "maxItems" -> {
                    if (!value.isIntegralNumber() || value.intValue() < 0) {
                        throw new IllegalArgumentException(path + "." + key + "必须是非负整数");
                    }
                }
                case "format" -> {
                    if (!value.isTextual() || !SUPPORTED_FORMATS.contains(value.asText())) {
                        throw new IllegalArgumentException(path + ".format仅支持" + SUPPORTED_FORMATS);
                    }
                }
                default -> throw new IllegalArgumentException("不支持的字段约束: " + key);
            }
        });
        validateConstraintRange(constraints, "minimum", "maximum", path);
        validateConstraintRange(constraints, "minLength", "maxLength", path);
        validateConstraintRange(constraints, "minItems", "maxItems", path);
    }

    private void validateEnum(JsonNode values, String type, String path) {
        if (!values.isArray() || values.isEmpty()) {
            throw new IllegalArgumentException(path + "必须是非空数组");
        }
        for (JsonNode value : values) {
            boolean matches = switch (type) {
                case "string" -> value.isTextual();
                case "integer" -> value.isIntegralNumber();
                case "number" -> value.isNumber();
                case "boolean" -> value.isBoolean();
                case "object" -> value.isObject();
                case "array" -> value.isArray();
                default -> false;
            };
            if (!matches) {
                throw new IllegalArgumentException(path + "的值类型必须为" + type);
            }
        }
    }

    private void validateConstraintRange(JsonNode constraints, String minKey, String maxKey, String path) {
        JsonNode minimum = constraints.get(minKey);
        JsonNode maximum = constraints.get(maxKey);
        if (minimum != null && maximum != null
                && minimum.decimalValue().compareTo(maximum.decimalValue()) > 0) {
            throw new IllegalArgumentException(path + "." + minKey + "不能大于" + maxKey);
        }
    }

    private void validateConfiguredValue(String rawValue, String type, String rawConstraints, String path) {
        if (!StringUtils.hasText(rawValue)) {
            return;
        }
        JsonNode value;
        if ("string".equals(type)) {
            value = objectMapper.getNodeFactory().textNode(parseConfiguredString(rawValue));
        } else {
            try {
                value = objectMapper.readTree(rawValue);
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException(path + "必须是有效的" + type + "值", exception);
            }
        }
        boolean matches = switch (type) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            default -> false;
        };
        if (!matches) {
            throw new IllegalArgumentException(path + "类型必须为" + type);
        }
        validateConfiguredValueConstraints(value, rawConstraints, path);
    }

    private void validateArrayItems(String rawValue, String itemType, String path) {
        if (!StringUtils.hasText(rawValue) || !StringUtils.hasText(itemType)) {
            return;
        }
        try {
            JsonNode values = objectMapper.readTree(rawValue);
            for (int index = 0; index < values.size(); index++) {
                JsonNode value = values.get(index);
                boolean matches = switch (itemType) {
                    case "string" -> value.isTextual();
                    case "integer" -> value.isIntegralNumber();
                    case "number" -> value.isNumber();
                    case "boolean" -> value.isBoolean();
                    case "object" -> value.isObject();
                    default -> false;
                };
                if (!matches) {
                    throw new IllegalArgumentException(path + "[" + index + "]类型必须为" + itemType);
                }
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(path + "必须是有效数组", exception);
        }
    }

    private String parseConfiguredString(String rawValue) {
        try {
            JsonNode parsed = objectMapper.readTree(rawValue);
            return parsed != null && parsed.isTextual() ? parsed.asText() : rawValue;
        } catch (JsonProcessingException ignored) {
            return rawValue;
        }
    }

    private void validateConfiguredValueConstraints(JsonNode value, String rawConstraints, String path) {
        if (!StringUtils.hasText(rawConstraints)) {
            return;
        }
        try {
            JsonNode constraints = objectMapper.readTree(rawConstraints);
            JsonNode enumValues = constraints.get("enum");
            if (enumValues != null) {
                boolean matched = false;
                for (JsonNode allowed : enumValues) {
                    if (allowed.equals(value)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    throw new IllegalArgumentException(path + "不在enum允许值范围内");
                }
            }
            if (value.isTextual()) {
                String text = value.asText();
                validateSize(text.length(), constraints, "minLength", "maxLength", path);
                if (constraints.has("pattern") && !Pattern.matches(constraints.get("pattern").asText(), text)) {
                    throw new IllegalArgumentException(path + "不符合pattern约束");
                }
                if (constraints.has("format") && !matchesFormat(text, constraints.get("format").asText())) {
                    throw new IllegalArgumentException(path + "不符合" + constraints.get("format").asText() + "格式");
                }
            } else if (value.isNumber()) {
                if (constraints.has("minimum")
                        && value.decimalValue().compareTo(constraints.get("minimum").decimalValue()) < 0) {
                    throw new IllegalArgumentException(path + "不能小于" + constraints.get("minimum").asText());
                }
                if (constraints.has("maximum")
                        && value.decimalValue().compareTo(constraints.get("maximum").decimalValue()) > 0) {
                    throw new IllegalArgumentException(path + "不能大于" + constraints.get("maximum").asText());
                }
            } else if (value.isArray()) {
                validateSize(value.size(), constraints, "minItems", "maxItems", path);
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(path + "的约束配置不是有效JSON", exception);
        }
    }

    private void validateSize(int size, JsonNode constraints, String minKey, String maxKey, String path) {
        if (constraints.has(minKey) && size < constraints.get(minKey).intValue()) {
            throw new IllegalArgumentException(path + "长度不能小于" + constraints.get(minKey).asText());
        }
        if (constraints.has(maxKey) && size > constraints.get(maxKey).intValue()) {
            throw new IllegalArgumentException(path + "长度不能大于" + constraints.get(maxKey).asText());
        }
    }

    private boolean matchesFormat(String value, String format) {
        try {
            return switch (format) {
                case "date" -> { LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE); yield true; }
                case "date-time" -> { DateTimeFormatter.ISO_DATE_TIME.parse(value); yield true; }
                case "email" -> value.matches("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$");
                case "uri" -> URI.create(value).isAbsolute();
                case "uuid" -> UUID.fromString(value).toString().equalsIgnoreCase(value);
                case "ipv4" -> InetAddress.getByName(value).getHostAddress().equals(value) && value.contains(".");
                case "ipv6" -> value.contains(":") && InetAddress.getByName(value).getHostAddress().contains(":");
                default -> false;
            };
        } catch (Exception ignored) {
            return false;
        }
    }

    private String writeSchema(List<InterfaceParamDTO> fields) {
        try {
            return objectMapper.writeValueAsString(buildObjectSchema(fields));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("接口契约无法生成JSON Schema", e);
        }
    }

    private ObjectNode buildObjectSchema(List<InterfaceParamDTO> fields) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", true);
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = objectMapper.createArrayNode();
        fields.stream()
                .sorted(Comparator.comparing(field -> field.getSort() != null ? field.getSort() : 0))
                .forEach(field -> {
                    properties.set(field.getParamName(), buildFieldSchema(field));
                    if (Boolean.TRUE.equals(field.getRequired())) {
                        required.add(field.getParamName());
                    }
                });
        if (!required.isEmpty()) {
            schema.set("required", required);
        }
        return schema;
    }

    private ObjectNode buildFieldSchema(InterfaceParamDTO field) {
        String type = normalizeType(field.getParamType());
        ObjectNode schema;
        if ("object".equals(type)) {
            schema = buildObjectSchema(field.getChildren());
        } else if ("array".equals(type)) {
            schema = objectMapper.createObjectNode();
            schema.put("type", "array");
            String itemType = normalizeArrayItemType(field.getArrayItemType(), field.getChildren());
            if ("object".equals(itemType)) {
                schema.set("items", buildObjectSchema(field.getChildren()));
            } else if (itemType == null) {
                schema.set("items", objectMapper.createObjectNode());
            } else {
                schema.putObject("items").put("type", itemType);
            }
        } else {
            schema = objectMapper.createObjectNode();
            schema.put("type", type);
        }
        if (StringUtils.hasText(field.getDescription())) {
            schema.put("description", field.getDescription());
        }
        putJsonValue(schema, "default", field.getDefaultValue(), type);
        putJsonValue(schema, "example", field.getExampleValue(), type);
        applyConstraints(schema, field.getConstraintConfig());
        return schema;
    }

    private void putJsonValue(ObjectNode target, String name, String rawValue, String type) {
        if (!StringUtils.hasText(rawValue)) {
            return;
        }
        if ("string".equals(type)) {
            try {
                JsonNode value = objectMapper.readTree(rawValue);
                if (value.isTextual()) {
                    target.set(name, value);
                    return;
                }
            } catch (JsonProcessingException ignored) {
                // 普通字符串无需使用JSON引号包裹。
            }
            target.put(name, rawValue);
            return;
        }
        try {
            target.set(name, objectMapper.readTree(rawValue));
        } catch (JsonProcessingException ignored) {
            target.put(name, rawValue);
        }
    }

    private void applyConstraints(ObjectNode target, String rawConstraints) {
        if (StringUtils.hasText(rawConstraints)) {
            try {
                JsonNode constraints = objectMapper.readTree(rawConstraints);
                constraints.fields().forEachRemaining(entry -> {
                    if (CONSTRAINT_KEYS.contains(entry.getKey())) {
                        target.set(entry.getKey(), entry.getValue());
                    }
                });
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("constraintConfig必须是JSON对象", e);
            }
        }
    }

    private String normalizeConstraints(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!node.isObject()) {
                throw new IllegalArgumentException("constraintConfig必须是JSON对象");
            }
            Iterator<String> keys = node.fieldNames();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!CONSTRAINT_KEYS.contains(key)) {
                    throw new IllegalArgumentException("不支持的字段约束: " + key);
                }
            }
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("constraintConfig必须是有效JSON", e);
        }
    }

    private String normalizeType(String type) {
        return StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "string";
    }

    private String normalizeArrayItemType(String itemType, List<InterfaceParamDTO> children) {
        if (children != null && !children.isEmpty()) {
            return "object";
        }
        return StringUtils.hasText(itemType) ? normalizeType(itemType) : null;
    }

    private String configuredValueText(JsonNode value, String type) {
        return "string".equals(type) && value.isTextual() ? value.asText() : value.toString();
    }

    private String defaultDirection(String direction) {
        return StringUtils.hasText(direction) ? direction.toUpperCase(Locale.ROOT) : REQUEST;
    }
}
