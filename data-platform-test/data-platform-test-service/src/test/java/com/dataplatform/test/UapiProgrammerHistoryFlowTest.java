package com.dataplatform.test;

import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UAPI「程序员历史上的今天」真实外部数据源端到端测试。
 *
 * <p>前置条件：已应用 V017 迁移，并存在可用于 OpenAPI 验证的调用方、产品和场景。
 * 默认使用本地初始化数据 demo-caller / loan-risk / pre-loan-review，可通过
 * TEST_UAPI_CALLER_CODE、TEST_UAPI_PRODUCT_CODE、TEST_UAPI_SCENE_CODE 覆盖。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("UAPI 程序员历史外部数据源全流程测试")
class UapiProgrammerHistoryFlowTest extends BaseTest {

    private static final String VENDOR_CODE = "uapi";
    private static final String DATA_TYPE_CODE = "programmer_history";
    private static final String API_CODE = "PROGRAMMER_HISTORY_TODAY";
    private static final String CALLER_CODE = setting(
            "test.uapi.caller-code", "TEST_UAPI_CALLER_CODE", "demo-caller");
    private static final String PRODUCT_CODE = setting(
            "test.uapi.product-code", "TEST_UAPI_PRODUCT_CODE", "loan-risk");
    private static final String SCENE_CODE = setting(
            "test.uapi.scene-code", "TEST_UAPI_SCENE_CODE", "pre-loan-review");

    private Long apiKeyId;

    @Test
    @DisplayName("厂商配置、真实调用、响应契约、调用记录和零元计费全部闭环")
    void completesExternalProviderFlow() {
        Map<String, Object> vendor = findByField(
                getAuthRequest().queryParam("page", 1).queryParam("pageSize", 100)
                        .get("/vendor/list"),
                "vendorCode", VENDOR_CODE);
        Long vendorId = longValue(vendor.get("id"));
        assertNotNull(vendorId, "UAPI 厂商缺少ID");
        assertEquals("active", vendor.get("status"));

        Map<String, Object> apiInterface = findByField(
                getAuthRequest().queryParam("vendorId", vendorId).queryParam("status", "active")
                        .get("/interface/options"),
                "interfaceCode", API_CODE);
        Long interfaceId = longValue(apiInterface.get("id"));
        Long dataTypeId = longValue(apiInterface.get("dataTypeId"));
        assertNotNull(interfaceId, "程序员历史接口缺少ID");
        assertNotNull(dataTypeId, "程序员历史接口缺少数据类型ID");

        Response contract = getAuthRequest().get("/interface/" + interfaceId + "/contract");
        verifySuccess(contract);
        contract.then()
                .body("data.responseFields.paramName", hasItem("message"))
                .body("data.responseFields.paramName", hasItem("date"))
                .body("data.responseFields.paramName", hasItem("events"));

        Response configs = getAuthRequest()
                .queryParam("vendorId", vendorId)
                .queryParam("interfaceId", interfaceId)
                .queryParam("status", "active")
                .get("/vendor/config/list");
        verifySuccess(configs);
        List<Map<String, Object>> configRows = configs.jsonPath().getList("data");
        assertTrue(configRows != null && configRows.size() == 1, "UAPI 应只有一条启用配置");
        Long configId = longValue(configRows.getFirst().get("id"));
        assertEquals("GET", configRows.getFirst().get("method"));
        assertEquals(dataTypeId, longValue(configRows.getFirst().get("dataTypeId")));
        assertEquals("https://uapis.cn/api/v1/history/programmer/today",
                configRows.getFirst().get("apiUrl"));

        Response health = getAuthRequest().post("/vendor/config/" + configId + "/test");
        verifySuccess(health);
        health.then()
                .body("data.success", equalTo(true))
                .body("data.data.message", startsWith("获取成功"))
                .body("data.data.events.size()", greaterThan(0));

        Map<String, Object> caller = findByField(
                getAuthRequest().queryParam("page", 1).queryParam("pageSize", 100)
                        .get("/caller/list"),
                "callerCode", CALLER_CODE);
        Long callerId = longValue(caller.get("id"));
        assertNotNull(callerId, "缺少端到端测试调用方: " + CALLER_CODE);

        Map<String, Object> product = findByField(
                getAuthRequest().get("/caller/" + callerId + "/products"),
                "productCode", PRODUCT_CODE);
        Long productId = longValue(product.get("id"));
        assertNotNull(productId, "缺少端到端测试产品: " + PRODUCT_CODE);

        Map<String, Object> scene = findByField(
                getAuthRequest().get("/call-scene/list"),
                "sceneCode", SCENE_CODE);
        assertNotNull(longValue(scene.get("id")), "缺少端到端测试场景: " + SCENE_CODE);

        Response keyResponse = getAuthRequest()
                .body(Map.of(
                        "callerId", callerId,
                        "name", "UAPI 程序员历史端到端测试"))
                .post("/caller/apikey");
        verifySuccess(keyResponse);
        apiKeyId = keyResponse.jsonPath().getLong("data.id");
        String apiKey = keyResponse.jsonPath().getString("data.apiKey");
        assertNotNull(apiKeyId, "API Key 创建后缺少ID");
        assertTrue(apiKey != null && !apiKey.isBlank(), "API Key 创建后缺少完整密钥");

        verifySuccess(getAuthRequest().body(List.of(productId))
                .post("/caller/apikey/" + apiKeyId + "/products"));
        verifySuccess(getAuthRequest().body(List.of(interfaceId))
                .post("/caller/apikey/" + apiKeyId + "/interfaces"));

        String traceId = "uapi-programmer-history-" + System.currentTimeMillis();
        Response query = given()
                .baseUri(GATEWAY_URL)
                .basePath("")
                .contentType("application/json")
                .header("X-Api-Key", apiKey)
                .header("X-Trace-Id", traceId)
                .body(Map.of(
                        "requestId", "uapi-e2e-" + System.currentTimeMillis(),
                        "apiCode", API_CODE,
                        "apiVersion", "v1",
                        "productCode", PRODUCT_CODE,
                        "sceneCode", SCENE_CODE,
                        "useCache", false,
                        "params", Map.of()))
                .post("/openapi/v1/query");

        query.then()
                .statusCode(200)
                .body("code", equalTo(200))
                .body("data.success", equalTo(true))
                .body("data.apiCode", equalTo(API_CODE))
                .body("data.cost", equalTo(0.0f))
                .body("data.data.message", startsWith("获取成功"))
                .body("data.data.date", notNullValue())
                .body("data.data.events.size()", greaterThan(0))
                .body("data.data.events[0].year", notNullValue())
                .body("data.data.events[0].title", notNullValue());

        String platformRequestId = query.jsonPath().getString("data.platformRequestId");
        assertNotNull(platformRequestId, "平台响应缺少 platformRequestId");
        awaitCallRecord(callerId, platformRequestId, traceId);
        assertBillingClosedLoop(vendorId);
    }

    @AfterAll
    void removeTemporaryApiKey() {
        if (apiKeyId == null || authToken == null) {
            return;
        }
        getAuthRequest().body(List.of()).post("/caller/apikey/" + apiKeyId + "/interfaces");
        getAuthRequest().body(List.of()).post("/caller/apikey/" + apiKeyId + "/products");
        getAuthRequest().delete("/caller/apikey/" + apiKeyId);
        apiKeyId = null;
    }

    private void awaitCallRecord(Long callerId, String platformRequestId, String traceId) {
        for (int attempt = 0; attempt < 20; attempt++) {
            Response records = getAuthRequest()
                    .queryParam("callerId", callerId)
                    .queryParam("apiCode", API_CODE)
                    .queryParam("page", 1)
                    .queryParam("pageSize", 20)
                    .get("/call-record/list");
            verifySuccess(records);
            List<Map<String, Object>> rows = records.jsonPath().getList("data");
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    if (platformRequestId.equals(row.get("requestId"))) {
                        assertEquals(VENDOR_CODE, row.get("vendorCode"));
                        assertEquals(DATA_TYPE_CODE, row.get("dataTypeCode"));
                        assertEquals(traceId, row.get("traceId"));
                        assertEquals(Boolean.TRUE, row.get("success"));
                        assertEquals(Boolean.TRUE, row.get("responseContractValid"));
                        assertEquals(0, decimalValue(row.get("cost")).compareTo(BigDecimal.ZERO));
                        return;
                    }
                }
            }
            pauseForAsyncWrite();
        }
        throw new AssertionError("Kafka 调用记录未在超时时间内落库: " + platformRequestId);
    }

    private void assertBillingClosedLoop(Long vendorId) {
        String today = LocalDate.now().toString();
        Response billing = getAuthRequest()
                .queryParam("vendorId", vendorId)
                .queryParam("startDate", today)
                .queryParam("endDate", today)
                .queryParam("page", 1)
                .queryParam("pageSize", 20)
                .get("/billing/list");
        verifySuccess(billing);
        List<Map<String, Object>> rows = billing.jsonPath().getList("data");
        assertTrue(rows != null && rows.stream().anyMatch(row ->
                        DATA_TYPE_CODE.equals(row.get("dataType"))
                                && decimalValue(row.get("totalCost")).compareTo(BigDecimal.ZERO) == 0),
                "未找到 UAPI 零元计费日聚合");
    }

    private Map<String, Object> findByField(Response response, String field, String expected) {
        verifySuccess(response);
        List<Map<String, Object>> rows = response.jsonPath().getList("data");
        assertNotNull(rows, "响应 data 不是列表");
        return rows.stream()
                .filter(row -> expected.equals(row.get(field)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到 " + field + "=" + expected));
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static BigDecimal decimalValue(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private static void pauseForAsyncWrite() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待异步调用记录时被中断", e);
        }
    }

    private static String setting(String property, String environment, String defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
