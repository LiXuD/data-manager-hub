package com.dataplatform.common.mapping;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("请求参数映射处理器测试")
class RequestMappingProcessorTest {

    private final RequestMappingProcessor processor = new RequestMappingProcessor();

    @Nested
    @DisplayName("基本映射")
    class BasicMapping {

        @Test
        @DisplayName("简单字段映射: entName → keyword")
        void shouldMapSimpleField() {
            RequestMappingItem item = new RequestMappingItem();
            item.setTargetField("keyword");
            item.setSourceVar("entName");

            Map<String, Object> request = new HashMap<>();
            request.put("entName", "北京科技");

            Map<String, Object> result = processor.mapRequest(request, List.of(item));

            assertEquals("北京科技", result.get("keyword"));
            assertNull(result.get("entName"));
        }

        @Test
        @DisplayName("多字段映射")
        void shouldMapMultipleFields() {
            RequestMappingItem item1 = new RequestMappingItem();
            item1.setTargetField("keyword");
            item1.setSourceVar("entName");

            RequestMappingItem item2 = new RequestMappingItem();
            item2.setTargetField("searchType");
            item2.setSourceVar("searchMode");

            Map<String, Object> request = new HashMap<>();
            request.put("entName", "北京科技");
            request.put("searchMode", "exact");

            Map<String, Object> result = processor.mapRequest(request, List.of(item1, item2));

            assertEquals("北京科技", result.get("keyword"));
            assertEquals("exact", result.get("searchType"));
        }

        @Test
        @DisplayName("空映射列表返回空结果")
        void shouldReturnEmptyWhenNoMappings() {
            Map<String, Object> request = new HashMap<>();
            request.put("entName", "北京科技");

            Map<String, Object> result = processor.mapRequest(request, List.of());

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("默认值处理")
    class DefaultValue {

        @Test
        @DisplayName("变量不存在时使用默认值")
        void shouldUseDefaultValueWhenVarMissing() {
            RequestMappingItem item = new RequestMappingItem();
            item.setTargetField("searchType");
            item.setSourceVar("searchMode");
            item.setDefaultValue("exact");

            Map<String, Object> request = new HashMap<>();

            Map<String, Object> result = processor.mapRequest(request, List.of(item));

            assertEquals("exact", result.get("searchType"));
        }

        @Test
        @DisplayName("变量存在时不使用默认值")
        void shouldNotUseDefaultValueWhenVarPresent() {
            RequestMappingItem item = new RequestMappingItem();
            item.setTargetField("searchType");
            item.setSourceVar("searchMode");
            item.setDefaultValue("exact");

            Map<String, Object> request = new HashMap<>();
            request.put("searchMode", "fuzzy");

            Map<String, Object> result = processor.mapRequest(request, List.of(item));

            assertEquals("fuzzy", result.get("searchType"));
        }

        @Test
        @DisplayName("变量为null时不使用默认值而是保留null")
        void shouldKeepNullWhenVarIsNull() {
            RequestMappingItem item = new RequestMappingItem();
            item.setTargetField("keyword");
            item.setSourceVar("entName");

            Map<String, Object> request = new HashMap<>();
            request.put("entName", null);

            Map<String, Object> result = processor.mapRequest(request, List.of(item));

            assertNull(result.get("keyword"));
        }
    }

    @Nested
    @DisplayName("必填参数校验")
    class RequiredValidation {

        @Test
        @DisplayName("必填参数缺失时抛出异常")
        void shouldThrowWhenRequiredParamMissing() {
            RequestMappingItem item = new RequestMappingItem();
            item.setTargetField("keyword");
            item.setSourceVar("entName");
            item.setRequired(true);

            Map<String, Object> request = new HashMap<>();

            MappingException ex = assertThrows(MappingException.class,
                () -> processor.mapRequest(request, List.of(item)));
            assertTrue(ex.getMessage().contains("entName"));
        }

        @Test
        @DisplayName("非必填参数缺失时不抛异常")
        void shouldNotThrowWhenOptionalParamMissing() {
            RequestMappingItem item = new RequestMappingItem();
            item.setTargetField("keyword");
            item.setSourceVar("entName");
            item.setRequired(false);

            Map<String, Object> request = new HashMap<>();

            Map<String, Object> result = processor.mapRequest(request, List.of(item));

            assertFalse(result.containsKey("keyword"));
        }

        @Test
        @DisplayName("required默认为true")
        void shouldDefaultRequiredToTrue() {
            RequestMappingItem item = new RequestMappingItem();
            item.setTargetField("keyword");
            item.setSourceVar("entName");

            Map<String, Object> request = new HashMap<>();

            assertThrows(MappingException.class,
                () -> processor.mapRequest(request, List.of(item)));
        }
    }

    @Nested
    @DisplayName("值转换")
    class TransformValue {

        @Test
        @DisplayName("uppercase 转换")
        void shouldTransformToUppercase() {
            RequestMappingItem item = new RequestMappingItem();
            item.setTargetField("keyword");
            item.setSourceVar("entName");
            item.setTransformType("uppercase");

            Map<String, Object> request = new HashMap<>();
            request.put("entName", "beijing");

            Map<String, Object> result = processor.mapRequest(request, List.of(item));

            assertEquals("BEIJING", result.get("keyword"));
        }

        @Test
        @DisplayName("lowercase 转换")
        void shouldTransformToLowercase() {
            RequestMappingItem item = new RequestMappingItem();
            item.setTargetField("keyword");
            item.setSourceVar("entName");
            item.setTransformType("lowercase");

            Map<String, Object> request = new HashMap<>();
            request.put("entName", "BEIJING");

            Map<String, Object> result = processor.mapRequest(request, List.of(item));

            assertEquals("beijing", result.get("keyword"));
        }

        @Test
        @DisplayName("trim 转换")
        void shouldTransformWithTrim() {
            RequestMappingItem item = new RequestMappingItem();
            item.setTargetField("keyword");
            item.setSourceVar("entName");
            item.setTransformType("trim");

            Map<String, Object> request = new HashMap<>();
            request.put("entName", "  北京科技  ");

            Map<String, Object> result = processor.mapRequest(request, List.of(item));

            assertEquals("北京科技", result.get("keyword"));
        }

        @Test
        @DisplayName("none 不做转换")
        void shouldNotTransformWhenNone() {
            RequestMappingItem item = new RequestMappingItem();
            item.setTargetField("keyword");
            item.setSourceVar("entName");
            item.setTransformType("none");

            Map<String, Object> request = new HashMap<>();
            request.put("entName", "Beijing");

            Map<String, Object> result = processor.mapRequest(request, List.of(item));

            assertEquals("Beijing", result.get("keyword"));
        }
    }

    @Nested
    @DisplayName("综合场景")
    class ComplexScenarios {

        @Test
        @DisplayName("必填参数缺失但有默认值时使用默认值")
        void shouldUseDefaultWhenRequiredButMissingWithDefault() {
            RequestMappingItem item = new RequestMappingItem();
            item.setTargetField("pageSize");
            item.setSourceVar("limit");
            item.setRequired(true);
            item.setDefaultValue("10");

            Map<String, Object> request = new HashMap<>();

            Map<String, Object> result = processor.mapRequest(request, List.of(item));

            assertEquals("10", result.get("pageSize"));
        }

        @Test
        @DisplayName("数值类型值保持不变")
        void shouldPreserveNumericValue() {
            RequestMappingItem item = new RequestMappingItem();
            item.setTargetField("pageSize");
            item.setSourceVar("limit");

            Map<String, Object> request = new HashMap<>();
            request.put("limit", 20);

            Map<String, Object> result = processor.mapRequest(request, List.of(item));

            assertEquals(20, result.get("pageSize"));
        }

        @Test
        @DisplayName("null 请求参数返回空映射")
        void shouldHandleNullRequest() {
            RequestMappingItem item = new RequestMappingItem();
            item.setTargetField("keyword");
            item.setSourceVar("entName");
            item.setRequired(false);

            Map<String, Object> result = processor.mapRequest(null, List.of(item));

            assertFalse(result.containsKey("keyword"));
        }
    }
}
