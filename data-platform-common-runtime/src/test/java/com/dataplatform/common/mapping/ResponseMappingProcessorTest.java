package com.dataplatform.common.mapping;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("响应参数映射处理器测试")
class ResponseMappingProcessorTest {

    private final ResponseMappingProcessor processor = new ResponseMappingProcessor();

    @Nested
    @DisplayName("普通字段映射")
    class FieldMapping {

        @Test
        @DisplayName("简单字段映射: ent_name → companyName")
        void shouldMapSimpleField() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("companyName");
            item.setSourcePath("ent_name");
            item.setSourceType("field");

            Map<String, Object> response = new HashMap<>();
            response.put("ent_name", "北京科技");

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertEquals("北京科技", result.get("companyName"));
        }

        @Test
        @DisplayName("多字段映射")
        void shouldMapMultipleFields() {
            ResponseMappingItem item1 = new ResponseMappingItem();
            item1.setTargetField("companyName");
            item1.setSourcePath("ent_name");
            item1.setSourceType("field");

            ResponseMappingItem item2 = new ResponseMappingItem();
            item2.setTargetField("capital");
            item2.setSourcePath("reg_cap");
            item2.setSourceType("field");

            Map<String, Object> response = new HashMap<>();
            response.put("ent_name", "北京科技");
            response.put("reg_cap", 1000000);

            Map<String, Object> result = processor.mapResponse(response, List.of(item1, item2));

            assertEquals("北京科技", result.get("companyName"));
            assertEquals(1000000, result.get("capital"));
        }

        @Test
        @DisplayName("嵌套字段映射: data.name → name (点号分隔)")
        void shouldMapNestedField() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("companyName");
            item.setSourcePath("data.name");
            item.setSourceType("field");

            Map<String, Object> response = new HashMap<>();
            response.put("data", Map.of("name", "北京科技"));

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertEquals("北京科技", result.get("companyName"));
        }

        @Test
        @DisplayName("深层嵌套字段映射")
        void shouldMapDeeplyNestedField() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("legalPerson");
            item.setSourcePath("data.company.legalPerson");
            item.setSourceType("field");

            Map<String, Object> response = new HashMap<>();
            response.put("data", Map.of("company", Map.of("legalPerson", "张三")));

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertEquals("张三", result.get("legalPerson"));
        }
    }

    @Nested
    @DisplayName("JSONPath 映射")
    class JsonPathMapping {

        @Test
        @DisplayName("JSONPath 提取嵌套字段")
        void shouldExtractWithJsonPath() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("companyName");
            item.setSourcePath("$.data.name");
            item.setSourceType("jsonPath");

            Map<String, Object> response = new HashMap<>();
            response.put("data", Map.of("name", "北京科技"));

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertEquals("北京科技", result.get("companyName"));
        }

        @Test
        @DisplayName("JSONPath 提取数组元素")
        void shouldExtractArrayElementWithJsonPath() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("firstName");
            item.setSourcePath("$.data.list[0].name");
            item.setSourceType("jsonPath");

            Map<String, Object> response = new HashMap<>();
            response.put("data", Map.of("list", List.of(Map.of("name", "张三"))));

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertEquals("张三", result.get("firstName"));
        }

        @Test
        @DisplayName("JSONPath 路径不存在时返回null")
        void shouldReturnNullWhenJsonPathNotFound() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("companyName");
            item.setSourcePath("$.data.notExist");
            item.setSourceType("jsonPath");

            Map<String, Object> response = new HashMap<>();
            response.put("data", Map.of("name", "北京科技"));

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertNull(result.get("companyName"));
        }

        @Test
        @DisplayName("无效 JSONPath 语法返回null")
        void shouldReturnNullForInvalidJsonPath() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("companyName");
            item.setSourcePath("$[invalid");
            item.setSourceType("jsonPath");

            Map<String, Object> response = new HashMap<>();
            response.put("data", Map.of("name", "北京科技"));

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertNull(result.get("companyName"));
        }
    }

    @Nested
    @DisplayName("默认值处理")
    class DefaultValue {

        @Test
        @DisplayName("字段不存在时使用默认值")
        void shouldUseDefaultValueWhenFieldMissing() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("companyName");
            item.setSourcePath("ent_name");
            item.setSourceType("field");
            item.setDefaultValue("未知");

            Map<String, Object> response = new HashMap<>();

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertEquals("未知", result.get("companyName"));
        }

        @Test
        @DisplayName("字段存在时不使用默认值")
        void shouldNotUseDefaultValueWhenFieldPresent() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("companyName");
            item.setSourcePath("ent_name");
            item.setSourceType("field");
            item.setDefaultValue("未知");

            Map<String, Object> response = new HashMap<>();
            response.put("ent_name", "北京科技");

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertEquals("北京科技", result.get("companyName"));
        }

        @Test
        @DisplayName("数值类型默认值")
        void shouldUseNumericDefaultValue() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("capital");
            item.setSourcePath("reg_cap");
            item.setSourceType("field");
            item.setDefaultValue(0);

            Map<String, Object> response = new HashMap<>();

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertEquals(0, result.get("capital"));
        }
    }

    @Nested
    @DisplayName("值转换")
    class TransformValue {

        @Test
        @DisplayName("toString 转换")
        void shouldTransformToString() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("capital");
            item.setSourcePath("reg_cap");
            item.setSourceType("field");
            item.setTransformType("toString");

            Map<String, Object> response = new HashMap<>();
            response.put("reg_cap", 1000000);

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertEquals("1000000", result.get("capital"));
        }

        @Test
        @DisplayName("toNumber 转换 - 字符串转数字")
        void shouldTransformToNumber() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("capital");
            item.setSourcePath("reg_cap");
            item.setSourceType("field");
            item.setTransformType("toNumber");

            Map<String, Object> response = new HashMap<>();
            response.put("reg_cap", "1000000");

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertInstanceOf(Number.class, result.get("capital"));
        }

        @Test
        @DisplayName("toNumber 转换 - 已经是数字时保持不变")
        void shouldKeepNumberWhenAlreadyNumber() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("capital");
            item.setSourcePath("reg_cap");
            item.setSourceType("field");
            item.setTransformType("toNumber");

            Map<String, Object> response = new HashMap<>();
            response.put("reg_cap", 1000000);

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertEquals(1000000, result.get("capital"));
        }

        @Test
        @DisplayName("toNumber 转换 - 非数字字符串保留原值")
        void shouldKeepOriginalWhenNotNumber() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("capital");
            item.setSourcePath("reg_cap");
            item.setSourceType("field");
            item.setTransformType("toNumber");

            Map<String, Object> response = new HashMap<>();
            response.put("reg_cap", "not-a-number");

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertEquals("not-a-number", result.get("capital"));
        }

        @Test
        @DisplayName("none 不做转换")
        void shouldNotTransformWhenNone() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("capital");
            item.setSourcePath("reg_cap");
            item.setSourceType("field");
            item.setTransformType("none");

            Map<String, Object> response = new HashMap<>();
            response.put("reg_cap", "1000000");

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertEquals("1000000", result.get("capital"));
        }
    }

    @Nested
    @DisplayName("综合场景")
    class ComplexScenarios {

        @Test
        @DisplayName("混合 field 和 jsonPath 映射")
        void shouldMixFieldAndJsonPathMappings() {
            ResponseMappingItem item1 = new ResponseMappingItem();
            item1.setTargetField("companyName");
            item1.setSourcePath("ent_name");
            item1.setSourceType("field");

            ResponseMappingItem item2 = new ResponseMappingItem();
            item2.setTargetField("legalPerson");
            item2.setSourcePath("$.data.legalPerson");
            item2.setSourceType("jsonPath");

            Map<String, Object> response = new HashMap<>();
            response.put("ent_name", "北京科技");
            response.put("data", Map.of("legalPerson", "张三"));

            Map<String, Object> result = processor.mapResponse(response, List.of(item1, item2));

            assertEquals("北京科技", result.get("companyName"));
            assertEquals("张三", result.get("legalPerson"));
        }

        @Test
        @DisplayName("空映射列表返回空结果")
        void shouldReturnEmptyWhenNoMappings() {
            Map<String, Object> response = new HashMap<>();
            response.put("ent_name", "北京科技");

            Map<String, Object> result = processor.mapResponse(response, List.of());

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("null 响应返回空映射")
        void shouldHandleNullResponse() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("companyName");
            item.setSourcePath("ent_name");
            item.setSourceType("field");

            Map<String, Object> result = processor.mapResponse(null, List.of(item));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("sourceType 默认为 field")
        void shouldDefaultSourceTypeToField() {
            ResponseMappingItem item = new ResponseMappingItem();
            item.setTargetField("companyName");
            item.setSourcePath("ent_name");

            Map<String, Object> response = new HashMap<>();
            response.put("ent_name", "北京科技");

            Map<String, Object> result = processor.mapResponse(response, List.of(item));

            assertEquals("北京科技", result.get("companyName"));
        }
    }
}
