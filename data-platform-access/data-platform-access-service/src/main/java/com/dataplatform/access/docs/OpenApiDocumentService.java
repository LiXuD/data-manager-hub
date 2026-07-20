package com.dataplatform.access.docs;

import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 根据接口契约动态生成单接口 OpenAPI 3.1 文档。 */
@Service
public class OpenApiDocumentService {

    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public OpenApiDocumentService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> describe(InterfaceContractDTO contract, String baseUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", contract);
        result.put("baseUrl", baseUrl);
        result.put("auth", Map.of(
                "type", "apiKey",
                "headers", List.of("X-Api-Key", "Authorization: Bearer <API_KEY>")));
        result.put("endpoints", List.of(
                Map.of("method", "POST", "path", "/openapi/v1/query", "name", "单笔查询"),
                Map.of("method", "POST", "path", "/openapi/v1/batch-query", "name", "批量查询")));
        result.put("errorCodes", List.of(
                Map.of("code", 400, "description", "请求参数或接口契约校验失败"),
                Map.of("code", 401, "description", "API Key无效或过期"),
                Map.of("code", 403, "description", "接口、产品或场景未授权"),
                Map.of("code", 404, "description", "接口配置不存在"),
                Map.of("code", 429, "description", "限流或配额不足")));
        result.put("curl", curlExample(contract, baseUrl));
        result.put("openapi", buildDocument(contract, baseUrl));
        return result;
    }

    public Map<String, Object> buildDocument(InterfaceContractDTO contract, String baseUrl) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("openapi", "3.1.0");
        document.put("info", Map.of(
                "title", contract.getInterfaceName() + " - 内部调用接口",
                "version", "v1",
                "description", contract.getDescription() != null ? contract.getDescription() : ""));
        document.put("servers", List.of(Map.of("url", baseUrl)));
        document.put("security", List.of(Map.of("ApiKeyAuth", List.of())));

        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("BusinessParams", readSchema(contract.getRequestSchema()));
        schemas.put("BusinessResponseData", readSchema(contract.getResponseSchema()));
        schemas.put("QueryRequest", queryRequestSchema(contract));
        schemas.put("QueryResponse", queryResponseSchema());
        schemas.put("BatchQueryRequest", batchRequestSchema(contract));
        schemas.put("BatchQueryResponse", batchResponseSchema());

        Map<String, Object> securitySchemes = Map.of("ApiKeyAuth", Map.of(
                "type", "apiKey", "in", "header", "name", "X-Api-Key"));
        document.put("components", Map.of("securitySchemes", securitySchemes, "schemas", schemas));

        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/openapi/v1/query", Map.of("post", operation(
                "调用" + contract.getInterfaceName(), "QueryRequest", "QueryResponse")));
        paths.put("/openapi/v1/batch-query", Map.of("post", operation(
                "批量调用" + contract.getInterfaceName(), "BatchQueryRequest", "BatchQueryResponse")));
        document.put("paths", paths);
        return document;
    }

    public String serialize(Map<String, Object> document, String format) {
        try {
            if ("yaml".equalsIgnoreCase(format) || "yml".equalsIgnoreCase(format)) {
                return yamlMapper.writeValueAsString(document);
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("OpenAPI文档序列化失败", e);
        }
    }

    private Map<String, Object> operation(String summary, String requestSchema, String responseSchema) {
        Map<String, Object> requestBody = Map.of(
                "required", true,
                "content", Map.of("application/json", Map.of(
                        "schema", Map.of("$ref", "#/components/schemas/" + requestSchema))));
        Map<String, Object> responses = new LinkedHashMap<>();
        Map<String, Object> responseContent = Map.of(
                "application/json", Map.of(
                        "schema", Map.of("$ref", "#/components/schemas/" + responseSchema)));
        responses.put("200", Map.of("description", "调用结果", "content", responseContent));
        responses.put("400", Map.of("description", "请求参数校验失败"));
        responses.put("401", Map.of("description", "API Key无效"));
        responses.put("403", Map.of("description", "无接口访问权限"));
        responses.put("429", Map.of("description", "限流或配额不足"));
        return Map.of("summary", summary, "requestBody", requestBody, "responses", responses);
    }

    private Map<String, Object> queryRequestSchema(InterfaceContractDTO contract) {
        Map<String, Object> properties = commonRequestProperties(contract);
        properties.put("params", Map.of("$ref", "#/components/schemas/BusinessParams"));
        return objectSchema(properties, List.of("apiCode", "productCode", "sceneCode", "params"));
    }

    private Map<String, Object> batchRequestSchema(InterfaceContractDTO contract) {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("itemId", Map.of("type", "string"));
        itemProperties.put("params", Map.of("$ref", "#/components/schemas/BusinessParams"));
        Map<String, Object> properties = commonRequestProperties(contract);
        properties.put("items", Map.of(
                "type", "array",
                "minItems", 1,
                "items", objectSchema(itemProperties, List.of("params"))));
        return objectSchema(properties, List.of("apiCode", "productCode", "sceneCode", "items"));
    }

    private Map<String, Object> commonRequestProperties(InterfaceContractDTO contract) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("requestId", Map.of("type", "string", "description", "调用方请求ID"));
        properties.put("apiCode", Map.of("type", "string", "const", contract.getInterfaceCode()));
        properties.put("apiVersion", Map.of("type", "string", "default", "v1"));
        properties.put("productCode", Map.of("type", "string"));
        properties.put("sceneCode", Map.of("type", "string"));
        properties.put("useCache", Map.of("type", "boolean", "default", false));
        properties.put("cacheDays", Map.of("type", "integer", "minimum", 1));
        return properties;
    }

    private Map<String, Object> queryResponseSchema() {
        Map<String, Object> dataProperties = new LinkedHashMap<>();
        dataProperties.put("requestId", Map.of("type", "string"));
        dataProperties.put("platformRequestId", Map.of("type", "string"));
        dataProperties.put("apiCode", Map.of("type", "string"));
        dataProperties.put("apiVersion", Map.of("type", "string"));
        dataProperties.put("success", Map.of("type", "boolean"));
        dataProperties.put("data", nullableBusinessResponseData());
        dataProperties.put("errorCode", nullableString());
        dataProperties.put("errorMsg", nullableString());
        dataProperties.put("cached", Map.of("type", "boolean"));
        dataProperties.put("durationMs", Map.of("type", "integer"));
        dataProperties.put("cost", Map.of("type", "number"));
        return resultEnvelope(objectSchema(dataProperties, List.of("success", "data")));
    }

    private Map<String, Object> batchResponseSchema() {
        Map<String, Object> dataProperties = new LinkedHashMap<>();
        dataProperties.put("requestId", Map.of("type", "string"));
        dataProperties.put("batchId", Map.of("type", "string"));
        dataProperties.put("apiCode", Map.of("type", "string"));
        dataProperties.put("total", Map.of("type", "integer"));
        dataProperties.put("success", Map.of("type", "integer"));
        dataProperties.put("failed", Map.of("type", "integer"));
        dataProperties.put("results", Map.of("type", "array", "items", batchResultItemSchema()));
        return resultEnvelope(objectSchema(dataProperties, List.of("total", "success", "failed", "results")));
    }

    private Map<String, Object> batchResultItemSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("itemId", nullableString());
        properties.put("requestId", Map.of("type", "string"));
        properties.put("success", Map.of("type", "boolean"));
        properties.put("cached", Map.of("type", "boolean"));
        properties.put("durationMs", Map.of("type", "integer"));
        properties.put("cost", Map.of("type", "number"));
        properties.put("data", nullableBusinessResponseData());
        properties.put("errorCode", nullableString());
        properties.put("errorMsg", nullableString());
        return objectSchema(properties, List.of("success", "data"));
    }

    private Map<String, Object> nullableBusinessResponseData() {
        return Map.of("oneOf", List.of(
                Map.of("$ref", "#/components/schemas/BusinessResponseData"),
                Map.of("type", "null")));
    }

    private Map<String, Object> resultEnvelope(Map<String, Object> dataSchema) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("code", Map.of("type", "integer"));
        properties.put("msg", Map.of("type", "string"));
        properties.put("data", dataSchema);
        return objectSchema(properties, List.of("code", "msg", "data"));
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        schema.put("additionalProperties", true);
        return schema;
    }

    private Map<String, Object> nullableString() {
        return Map.of("type", List.of("string", "null"));
    }

    private Map<String, Object> readSchema(String rawSchema) {
        if (!StringUtils.hasText(rawSchema)) {
            return objectSchema(new LinkedHashMap<>(), List.of());
        }
        try {
            return objectMapper.readValue(rawSchema, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("接口Schema不是有效JSON", e);
        }
    }

    private String curlExample(InterfaceContractDTO contract, String baseUrl) {
        return "curl -X POST '" + baseUrl + "/openapi/v1/query' \\\n"
                + "  -H 'Content-Type: application/json' \\\n"
                + "  -H 'X-Api-Key: <API_KEY>' \\\n"
                + "  -d '{\"apiCode\":\"" + contract.getInterfaceCode()
                + "\",\"productCode\":\"<PRODUCT_CODE>\",\"sceneCode\":\"<SCENE_CODE>\",\"params\":{}}'";
    }
}
