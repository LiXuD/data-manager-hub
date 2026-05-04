package com.dataplatform.test;

import com.dataplatform.common.adapter.HttpVendorAdapter;
import com.dataplatform.common.adapter.VendorAdapterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP厂商适配器测试
 */
@DisplayName("HTTP厂商适配器测试")
class HttpVendorAdapterTest {

    private HttpVendorAdapter adapter;
    private VendorAdapterConfig config;

    @BeforeEach
    void setUp() {
        adapter = new HttpVendorAdapter("TEST_VENDOR");

        config = new VendorAdapterConfig();
        config.setApiUrl("https://httpbin.org/post");
        config.setMethod("POST");
        config.setSecretKey("test_secret_key");
        config.setSignType("HMAC_SHA256");
    }

    @Test
    @DisplayName("获取厂商编码")
    void testGetVendorCode() {
        assertEquals("TEST_VENDOR", adapter.getVendorCode());
    }

    @Test
    @DisplayName("检查支持数据类型 - 默认支持所有")
    void testSupports() {
        assertTrue(adapter.supports("ANY_DATA_TYPE"));
        assertTrue(adapter.supports("PERSONAL_INFO"));
        assertTrue(adapter.supports("VEHICLE_INFO"));
    }

    @Test
    @DisplayName("转换请求参数 - 空映射")
    void testTransformRequest_EmptyMapping() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");
        params.put("idCard", "110101199001011234");

        Map<String, Object> result = adapter.transformRequest(params, null);

        assertEquals(params, result);
    }

    @Test
    @DisplayName("转换请求参数 - JSON映射")
    void testTransformRequest_JsonMapping() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");
        params.put("idCard", "110101199001011234");

        String mapping = "{\"name\":\"xm\",\"idCard\":\"sfzh\"}";
        Map<String, Object> result = adapter.transformRequest(params, mapping);

        assertEquals("张三", result.get("xm"));
        assertEquals("110101199001011234", result.get("sfzh"));
        assertNull(result.get("name"));
        assertNull(result.get("idCard"));
    }

    @Test
    @DisplayName("转换响应数据 - 空映射")
    void testTransformResponse_EmptyMapping() {
        Map<String, Object> response = new HashMap<>();
        response.put("code", "0");
        response.put("data", Map.of("result", "pass"));

        Map<String, Object> result = adapter.transformResponse(response, null);

        assertEquals(response, result);
    }

    @Test
    @DisplayName("转换响应数据 - JSON映射")
    void testTransformResponse_JsonMapping() {
        Map<String, Object> response = new HashMap<>();
        response.put("respCode", "0");
        response.put("respData", Map.of("result", "pass"));

        String mapping = "{\"respCode\":\"code\",\"respData\":\"data\"}";
        Map<String, Object> result = adapter.transformResponse(response, mapping);

        assertEquals("0", result.get("code"));
        assertNotNull(result.get("data"));
    }

    @Test
    @DisplayName("转换请求参数 - 结构化映射")
    void testTransformRequest_StructuredMapping() {
        Map<String, Object> params = new HashMap<>();
        params.put("entName", "北京科技");
        params.put("searchMode", "FUZZY");

        String mapping = "[{\"targetField\":\"keyword\",\"sourceVar\":\"entName\",\"required\":true}," +
            "{\"targetField\":\"type\",\"sourceVar\":\"searchMode\",\"defaultValue\":\"exact\",\"transformType\":\"lowercase\"}]";
        Map<String, Object> result = adapter.transformRequest(params, mapping);

        assertEquals("北京科技", result.get("keyword"));
        assertEquals("fuzzy", result.get("type"));
        assertNull(result.get("entName"));
    }

    @Test
    @DisplayName("转换响应数据 - 结构化映射")
    void testTransformResponse_StructuredMapping() {
        Map<String, Object> response = new HashMap<>();
        response.put("ent_name", "北京科技");
        response.put("data", Map.of("legalPerson", "张三"));

        String mapping = "[{\"targetField\":\"companyName\",\"sourcePath\":\"ent_name\",\"sourceType\":\"field\"}," +
            "{\"targetField\":\"legalPerson\",\"sourcePath\":\"$.data.legalPerson\",\"sourceType\":\"jsonPath\"}]";
        Map<String, Object> result = adapter.transformResponse(response, mapping);

        assertEquals("北京科技", result.get("companyName"));
        assertEquals("张三", result.get("legalPerson"));
    }

    @Test
    @DisplayName("执行请求 - 配置为空抛异常")
    void testExecute_NullConfig() {
        Map<String, Object> params = new HashMap<>();

        assertThrows(IllegalArgumentException.class, () ->
            adapter.execute(null, params)
        );
    }

    @Test
    @DisplayName("执行请求 - 无效URL")
    void testExecute_InvalidUrl() {
        config.setApiUrl("not-a-valid-url");
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");

        assertThrows(IllegalArgumentException.class, () ->
            adapter.execute(config, params)
        );
    }

    @Test
    @DisplayName("执行请求 - HTTP调用")
    void testExecute_HttpCall() {
        config.setApiUrl("https://httpbin.org/post");
        config.setSignType(null);
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");

        Map<String, Object> result = adapter.execute(config, params);

        assertNotNull(result);
        assertNotNull(result.get("latency"));
    }

    @Test
    @DisplayName("执行请求 - 带签名")
    void testExecute_WithSignature() {
        config.setApiUrl("https://httpbin.org/post");
        config.setSignType("HMAC_SHA256");
        config.setSecretKey("test_secret");
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");

        Map<String, Object> result = adapter.execute(config, params);

        assertNotNull(result);
    }

    @Test
    @DisplayName("执行请求 - GET方法")
    void testExecute_GetMethod() {
        config.setApiUrl("https://httpbin.org/get");
        config.setMethod("GET");
        config.setSignType(null);
        Map<String, Object> params = new HashMap<>();
        params.put("query", "test");

        Map<String, Object> result = adapter.execute(config, params);

        assertNotNull(result);
    }

    @Test
    @DisplayName("执行请求 - 自定义请求头")
    void testExecute_CustomHeaders() {
        config.setApiUrl("https://httpbin.org/post");
        config.setSignType(null);
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Custom-Header", "CustomValue");
        config.setHeaders(headers);
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");

        Map<String, Object> result = adapter.execute(config, params);

        assertNotNull(result);
    }

    @Test
    @DisplayName("执行请求 - 响应时间记录")
    void testExecute_LatencyRecorded() {
        config.setApiUrl("https://httpbin.org/post");
        config.setSignType(null);
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");

        Map<String, Object> result = adapter.execute(config, params);

        assertTrue(result.containsKey("latency"));
        Object latency = result.get("latency");
        assertTrue(latency instanceof Long);
        assertTrue((Long) latency >= 0);
    }
}
