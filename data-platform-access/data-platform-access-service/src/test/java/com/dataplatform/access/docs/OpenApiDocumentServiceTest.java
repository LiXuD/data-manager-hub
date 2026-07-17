package com.dataplatform.access.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenApiDocumentServiceTest {

    @Test
    void generatesImportablePerInterfaceOpenApiJsonAndYaml() {
        InterfaceContractDTO contract = new InterfaceContractDTO();
        contract.setInterfaceId(1L);
        contract.setInterfaceCode("PERSONAL_QUERY");
        contract.setInterfaceName("个人信息查询");
        contract.setRequestSchema("{\"type\":\"object\",\"properties\":{\"idCard\":{\"type\":\"string\"}},\"required\":[\"idCard\"]}");
        contract.setResponseSchema("{\"type\":\"object\",\"properties\":{\"score\":{\"type\":\"integer\"}}}");
        OpenApiDocumentService service = new OpenApiDocumentService(new ObjectMapper());

        Map<String, Object> document = service.buildDocument(contract, "https://gateway.internal");
        String json = service.serialize(document, "json");
        String yaml = service.serialize(document, "yaml");

        assertEquals("3.1.0", document.get("openapi"));
        assertTrue(json.contains("PERSONAL_QUERY"));
        assertTrue(json.contains("/openapi/v1/batch-query"));
        assertTrue(yaml.contains("openapi:"));
        assertTrue(yaml.contains("3.1.0"));

        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) document.get("components");
        @SuppressWarnings("unchecked")
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        @SuppressWarnings("unchecked")
        Map<String, Object> batchResponse = (Map<String, Object>) schemas.get("BatchQueryResponse");
        assertTrue(containsReference(batchResponse, "#/components/schemas/BusinessResponseData"));
    }

    @SuppressWarnings("unchecked")
    private boolean containsReference(Object value, String reference) {
        if (value instanceof Map<?, ?> map) {
            if (reference.equals(map.get("$ref"))) {
                return true;
            }
            return map.values().stream().anyMatch(item -> containsReference(item, reference));
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsReference(item, reference)) {
                    return true;
                }
            }
        }
        return false;
    }
}
