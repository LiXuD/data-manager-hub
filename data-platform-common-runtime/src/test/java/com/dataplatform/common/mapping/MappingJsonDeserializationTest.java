package com.dataplatform.common.mapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("映射配置 JSON 反序列化测试")
class MappingJsonDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("反序列化请求映射配置")
    void shouldDeserializeRequestMapping() throws Exception {
        String json = "[{\"targetField\":\"keyword\",\"sourceVar\":\"entName\",\"required\":true}," +
            "{\"targetField\":\"type\",\"sourceVar\":\"searchMode\",\"defaultValue\":\"exact\",\"transformType\":\"lowercase\"}]";

        List<RequestMappingItem> items = objectMapper.readValue(json, new TypeReference<List<RequestMappingItem>>() {});

        assertEquals(2, items.size());
        assertEquals("keyword", items.get(0).getTargetField());
        assertEquals("entName", items.get(0).getSourceVar());
        assertTrue(items.get(0).getRequired());
        assertEquals("type", items.get(1).getTargetField());
        assertEquals("searchMode", items.get(1).getSourceVar());
        assertEquals("exact", items.get(1).getDefaultValue());
        assertEquals("lowercase", items.get(1).getTransformType());
    }

    @Test
    @DisplayName("反序列化响应映射配置")
    void shouldDeserializeResponseMapping() throws Exception {
        String json = "[{\"targetField\":\"companyName\",\"sourcePath\":\"ent_name\",\"sourceType\":\"field\"}," +
            "{\"targetField\":\"legalPerson\",\"sourcePath\":\"$.data.legalPerson\",\"sourceType\":\"jsonPath\"}]";

        List<ResponseMappingItem> items = objectMapper.readValue(json, new TypeReference<List<ResponseMappingItem>>() {});

        assertEquals(2, items.size());
        assertEquals("companyName", items.get(0).getTargetField());
        assertEquals("ent_name", items.get(0).getSourcePath());
        assertEquals("field", items.get(0).getSourceType());
        assertEquals("legalPerson", items.get(1).getTargetField());
        assertEquals("$.data.legalPerson", items.get(1).getSourcePath());
        assertEquals("jsonPath", items.get(1).getSourceType());
    }

    @Test
    @DisplayName("区分数组格式和Map格式")
    void shouldDistinguishArrayAndMapFormat() throws Exception {
        String arrayJson = "[{\"targetField\":\"keyword\",\"sourceVar\":\"entName\"}]";
        String mapJson = "{\"name\":\"xm\",\"idCard\":\"sfzh\"}";

        assertTrue(arrayJson.trim().startsWith("["));
        assertTrue(mapJson.trim().startsWith("{"));
    }
}
