package com.dataplatform.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Release acceptance for one explicitly provisioned, migrated connector.
 *
 * <p>The target vector is supplied only by the protected {@code dmh-acceptance}
 * Secret.  It intentionally does not create or mutate vendor configuration:
 * staging/production acceptance must exercise a pre-approved synthetic or
 * vendor-safe account through the public Gateway and observe the resulting
 * management, call-record and billing facts.</p>
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("连接器产品模型发布验收")
class ConnectorProductFlowTest extends BaseTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RUN_ID = "connector-acceptance-" + System.currentTimeMillis();
    private static final String ACCEPTANCE_GATEWAY_URL = required("GATEWAY_URL");
    private static final long CONFIG_ID = requiredLong("TEST_CONNECTOR_VENDOR_CONFIG_ID");
    private static final String API_KEY = required("TEST_CONNECTOR_API_KEY");
    private static final String API_CODE = required("TEST_CONNECTOR_API_CODE");
    private static final String PRODUCT_CODE = required("TEST_CONNECTOR_PRODUCT_CODE");
    private static final String SCENE_CODE = required("TEST_CONNECTOR_SCENE_CODE");
    private static final String EXPECTED_PLUGIN_ID = required("TEST_CONNECTOR_EXPECTED_PLUGIN_ID");
    private static final String EXPECTED_PLUGIN_VERSION = required("TEST_CONNECTOR_EXPECTED_PLUGIN_VERSION");
    private static final List<String> EXPECTED_CAPABILITIES = csv("TEST_CONNECTOR_EXPECTED_CAPABILITIES");
    private static final String CACHE_PARAMS_JSON = required("TEST_CONNECTOR_CACHE_PARAMS_JSON");
    private static final String ERROR_PARAMS_JSON = required("TEST_CONNECTOR_ERROR_PARAMS_JSON");
    private static final String EXPECTED_ERROR_CODE = required("TEST_CONNECTOR_ERROR_CODE");
    private static final String FALLBACK_PARAMS_JSON = required("TEST_CONNECTOR_FALLBACK_PARAMS_JSON");
    private static final long EXPECTED_FALLBACK_CONFIG_ID =
            requiredLong("TEST_CONNECTOR_EXPECTED_FALLBACK_CONFIG_ID");
    private static final long EXPECTED_FALLBACK_VENDOR_ID =
            requiredLong("TEST_CONNECTOR_EXPECTED_FALLBACK_VENDOR_ID");
    private static final int FALLBACK_ATTEMPTS = requiredInt("TEST_CONNECTOR_FALLBACK_ATTEMPTS");
    private static final int EXPECTED_RAW_TEST_STATUS = requiredInt("TEST_CONNECTOR_RAW_TEST_EXPECTED_STATUS");
    private static final String EXPECTED_RAW_TEST_CODE = required("TEST_CONNECTOR_RAW_TEST_EXPECTED_CODE");
    private static final int CAPACITY_REQUESTS = requiredInt("TEST_CONNECTOR_CAPACITY_REQUESTS");
    private static final int CAPACITY_CONCURRENCY = requiredInt("TEST_CONNECTOR_CAPACITY_CONCURRENCY");
    private static final double CAPACITY_P95_LIMIT_MS = requiredDouble("TEST_CONNECTOR_CAPACITY_P95_LIMIT_MS");
    private static final String CAPACITY_PARAMS_JSON = required("TEST_CONNECTOR_CAPACITY_PARAMS_JSON");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private Long vendorId;
    private Long interfaceId;
    private Long activeConnectorVersionId;
    private String dataTypeCode;

    @Test
    @Order(1)
    @DisplayName("容量观察通过真实 Gateway 请求并落库完整连接器事实")
    void observesConfiguredCapacity() {
        loadConnectorFacts();
        assertTrue(CAPACITY_REQUESTS > 0, "容量观察请求数必须大于0");
        assertTrue(CAPACITY_CONCURRENCY > 0, "容量观察并发数必须大于0");
        assertTrue(CAPACITY_CONCURRENCY <= CAPACITY_REQUESTS,
                "容量观察并发数不能超过请求数");
        assertTrue(CAPACITY_REQUESTS <= 100,
                "容量观察请求数不能超过一次调用记录查询的安全页大小100");
        List<Future<CapacitySample>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(CAPACITY_CONCURRENCY);
        try {
            for (int index = 0; index < CAPACITY_REQUESTS; index++) {
                int requestNumber = index;
                futures.add(executor.submit(() -> capacityCall(requestNumber)));
            }

            List<CapacitySample> samples = new ArrayList<>();
            for (Future<CapacitySample> future : futures) {
                try {
                    samples.add(future.get(60, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("容量观察被中断", exception);
                } catch (ExecutionException | java.util.concurrent.TimeoutException exception) {
                    throw new AssertionError("容量观察请求未完成", exception);
                }
            }

            List<Double> durations = samples.stream()
                    .map(CapacitySample::durationMs)
                    .sorted()
                    .toList();
            int p95Index = Math.max(0, (int) Math.ceil(durations.size() * 0.95) - 1);
            double p95 = durations.get(p95Index);
            assertEquals(CAPACITY_REQUESTS, samples.size());
            samples.forEach(sample -> assertFalse(sample.platformRequestId().isBlank(),
                    "容量观察响应缺少platformRequestId"));
            assertTrue(samples.stream().allMatch(CapacitySample::successful),
                    "容量观察存在失败请求");
            assertTrue(p95 <= CAPACITY_P95_LIMIT_MS,
                    () -> "连接器容量观察P95超限: " + p95 + "ms > " + CAPACITY_P95_LIMIT_MS + "ms");
            awaitCallRecords(samples.stream().map(CapacitySample::platformRequestId).collect(java.util.stream.Collectors.toSet()),
                    CAPACITY_REQUESTS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Order(2)
    @DisplayName("清点、迁移、请求响应、错误、缓存、计费和主备事实闭环")
    void completesConnectorProductFlow() {
        loadConnectorFacts();

        Response interfaceResponse = adminGet("/interface/" + interfaceId);
        Map<String, Object> apiInterface = interfaceResponse.jsonPath().getMap("data");
        assertEquals(API_CODE, apiInterface.get("interfaceCode"));
        assertEquals(CONFIG_ID, longValue(apiInterface.get("primaryVendorConfigId")));
        assertEquals(EXPECTED_FALLBACK_CONFIG_ID,
                longValue(apiInterface.get("fallbackVendorConfigId")),
                "主备路由未绑定预期备用厂商");
        assertEquals("READY", stringValue(apiInterface.get("routingReadiness")));

        Response draftResponse = adminGet("/vendor/config/" + CONFIG_ID + "/connector-spec/draft");
        assertEquals(Boolean.TRUE, draftResponse.jsonPath().getBoolean("data.present"));
        assertEquals("SIMPLE_CONNECTOR", draftResponse.jsonPath().getString("data.authoringMode"));
        assertEquals(EXPECTED_PLUGIN_ID, draftResponse.jsonPath().getString("data.connectorSpec.plugin.pluginId"));
        assertEquals(EXPECTED_PLUGIN_VERSION,
                draftResponse.jsonPath().getString("data.connectorSpec.plugin.pluginVersion"));
        assertHash(draftResponse.jsonPath().getString("data.specHash"), "草稿specHash");
        assertHash(draftResponse.jsonPath().getString("data.compileHash"), "草稿compileHash");
        assertHash(draftResponse.jsonPath().getString("data.compiledSnapshotHash"), "草稿compiledSnapshotHash");

        Response planResponse = adminGet("/vendor/config/" + CONFIG_ID + "/connector-spec/execution-plan");
        List<Map<String, Object>> stages = planResponse.jsonPath().getList("data.stages");
        assertEquals(EXPECTED_CAPABILITIES.size(), stages.size(), "平台生成计划阶段数不匹配");
        for (int index = 0; index < EXPECTED_CAPABILITIES.size(); index++) {
            Map<String, Object> stage = stages.get(index);
            assertEquals(EXPECTED_CAPABILITIES.get(index), stage.get("capability"));
            assertEquals(EXPECTED_PLUGIN_ID, stage.get("pluginId"));
            assertEquals(EXPECTED_PLUGIN_VERSION, stage.get("pluginVersion"));
            assertHash(stringValue(stage.get("configHash")), "执行计划configHash");
        }
        assertFalse(planResponse.asString().contains("SecretRef"), "执行计划泄露SecretRef");

        Response inventoryResponse = adminGet("/vendor/config/connector-spec/inventory?page=1&pageSize=100");
        List<Map<String, Object>> inventory = inventoryResponse.jsonPath().getList("data.items");
        assertNotNull(inventory, "Legacy inventory缺少items");
        assertTrue(inventory.stream().noneMatch(item -> Long.valueOf(CONFIG_ID)
                        .equals(longValue(item.get("vendorConfigId")))),
                "已迁移SIMPLE配置仍出现在Legacy inventory");

        Response migrationResponse = getAuthRequest().queryParam("state", "STABLE")
                .get("/vendor/connector-migration");
        verifySuccess(migrationResponse);
        List<Map<String, Object>> migrations = migrationResponse.jsonPath().getList("data");
        Map<String, Object> migration = migrations.stream()
                .filter(item -> Long.valueOf(CONFIG_ID)
                        .equals(longValue(item.get("vendorConfigId"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到连接器稳定迁移记录"));
        assertEquals("STABLE", migration.get("state"));
        assertEquals(Boolean.TRUE, migration.get("observationGatePassed"));
        assertEquals(interfaceId, longValue(migration.get("interfaceId")));
        assertTrue(numberValue(migration.get("publishedConnectorVersionId")) > 0,
                "迁移稳定记录缺少已发布连接器版本");
        assertTrue(numberValue(migration.get("observedCalls")) >= 1, "迁移观察没有真实调用事实");
        assertTrue(numberValue(migration.get("observedBillingCoverageRate")) >= 1,
                "迁移观察缺少计费覆盖事实");

        assertRawWriteRetirementBoundary();

        assertTrue(CACHE_PARAMS_JSON.contains("${RUN_ID}"),
                "缓存验收参数必须包含${RUN_ID}以避免复用旧缓存");
        PublicCall cacheMiss = publicCall("cache-miss", true, CACHE_PARAMS_JSON);
        assertPublicSuccess(cacheMiss.response(), false, "缓存未命中");
        awaitCallRecord(
                platformRequestId(cacheMiss.response()), true, false, null, vendorId);

        PublicCall cacheHit = publicCall("cache-hit", true, CACHE_PARAMS_JSON);
        assertPublicSuccess(cacheHit.response(), true, "缓存命中");
        Map<String, Object> cacheHitRecord = awaitCallRecord(
                platformRequestId(cacheHit.response()), true, true, null, vendorId);
        assertEquals(0, decimalValue(cacheHit.response().jsonPath().get("data.cost")).compareTo(BigDecimal.ZERO),
                "缓存命中必须零计费");
        assertEquals(0, decimalValue(cacheHitRecord.get("cost")).compareTo(BigDecimal.ZERO),
                "缓存命中CallRecord必须零计费");

        PublicCall errorCall = publicCall("error", false, ERROR_PARAMS_JSON);
        assertPublicError(errorCall.response(), EXPECTED_ERROR_CODE);
        Map<String, Object> errorRecord = awaitCallRecord(
                platformRequestId(errorCall.response()), false, false, EXPECTED_ERROR_CODE, vendorId);
        assertEquals(0, decimalValue(errorRecord.get("cost")).compareTo(BigDecimal.ZERO),
                "失败请求不得计费");

        Map<String, Object> fallbackRecord = null;
        assertTrue(FALLBACK_ATTEMPTS > 0 && FALLBACK_ATTEMPTS <= 64,
                "备用路由观察次数必须在1到64之间");
        for (int attempt = 1; attempt <= FALLBACK_ATTEMPTS; attempt++) {
            PublicCall fallbackCall = publicCall("fallback-" + attempt, false, FALLBACK_PARAMS_JSON);
            if (isPublicSuccess(fallbackCall.response())) {
                assertEquals(false, fallbackCall.response().jsonPath().getBoolean("data.cached"));
                fallbackRecord = awaitCallRecord(
                        platformRequestId(fallbackCall.response()), true, false, null,
                        EXPECTED_FALLBACK_VENDOR_ID);
                break;
            }
        }
        assertNotNull(fallbackRecord, "熔断打开后备用厂商未返回真实成功");

        awaitBillingRow(vendorId, 1);
        awaitBillingRow(EXPECTED_FALLBACK_VENDOR_ID, 1);
    }

    private void assertRawWriteRetirementBoundary() {
        Response response = getAuthRequest()
                .body(Map.of("params", Map.of()))
                .post("/vendor/config/" + CONFIG_ID + "/connector/test");
        assertEquals(EXPECTED_RAW_TEST_STATUS, response.statusCode(), "raw测试退役边界状态码不匹配");
        assertEquals(EXPECTED_RAW_TEST_STATUS, response.jsonPath().getInt("code"));
        assertEquals(EXPECTED_RAW_TEST_CODE, messageOf(response));
    }

    private PublicCall publicCall(String label, boolean useCache, String paramsTemplate) {
        String requestId = RUN_ID + "-" + label;
        String traceId = RUN_ID + "-trace-" + label;
        String body = requestBody(requestId, useCache, paramsTemplate);
        Response response = io.restassured.RestAssured.given()
                .baseUri(ACCEPTANCE_GATEWAY_URL)
                .basePath("")
                .contentType("application/json")
                .header("X-Api-Key", API_KEY)
                .header("X-Trace-Id", traceId)
                .body(body)
                .post("/openapi/v1/query");
        return new PublicCall(requestId, traceId, response);
    }

    private CapacitySample capacityCall(int requestNumber) {
        String requestId = RUN_ID + "-capacity-" + requestNumber;
        String traceId = RUN_ID + "-capacity-trace-" + requestNumber;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/openapi/v1/query")))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-Api-Key", API_KEY)
                .header("X-Trace-Id", traceId)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(
                        requestId, false, CAPACITY_PARAMS_JSON)))
                .build();
        long started = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            double durationMs = (System.nanoTime() - started) / 1_000_000.0;
            JsonNode body = JSON.readTree(response.body());
            boolean successful = response.statusCode() == 200
                    && body.path("code").asInt() == 200
                    && body.path("data").path("success").asBoolean(false);
            String platformRequestId = body.path("data").path("platformRequestId").asText("");
            return new CapacitySample(successful, durationMs, platformRequestId);
        } catch (Exception exception) {
            throw new AssertionError("容量观察请求失败", exception);
        }
    }

    private void awaitCallRecords(Set<String> platformRequestIds, int expectedCount) {
        for (int attempt = 0; attempt < 40; attempt++) {
            Response response = getAuthRequest()
                    .queryParam("apiCode", API_CODE)
                    .queryParam("page", 1)
                    .queryParam("pageSize", 100)
                    .get("/call-record/list");
            verifySuccess(response);
            List<Map<String, Object>> rows = response.jsonPath().getList("data");
            long matched = rows == null ? 0 : rows.stream()
                    .filter(row -> platformRequestIds.contains(stringValue(row.get("requestId"))))
                    .count();
            if (matched >= expectedCount) {
                rows.stream()
                        .filter(row -> platformRequestIds.contains(stringValue(row.get("requestId"))))
                        .forEach(row -> {
                            assertConnectorFact(row, true, false, null, vendorId);
                        });
                return;
            }
            pauseForAsyncWrite();
        }
        throw new AssertionError("容量观察CallRecord未在超时时间内完整落库");
    }

    private Map<String, Object> awaitCallRecord(String platformRequestId, boolean expectedSuccess,
                                                 boolean expectedCacheHit, String expectedErrorCode,
                                                 Long expectedVendorId) {
        for (int attempt = 0; attempt < 40; attempt++) {
            Response response = getAuthRequest()
                    .queryParam("apiCode", API_CODE)
                    .queryParam("page", 1)
                    .queryParam("pageSize", 100)
                    .get("/call-record/list");
            verifySuccess(response);
            List<Map<String, Object>> rows = response.jsonPath().getList("data");
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    if (platformRequestId.equals(row.get("requestId"))) {
                        assertConnectorFact(row, expectedSuccess, expectedCacheHit,
                                expectedErrorCode, expectedVendorId);
                        return row;
                    }
                }
            }
            pauseForAsyncWrite();
        }
        throw new AssertionError("CallRecord未在超时时间内落库: " + platformRequestId);
    }

    private void loadConnectorFacts() {
        Response configResponse = adminGet("/vendor/config/" + CONFIG_ID);
        Map<String, Object> config = configResponse.jsonPath().getMap("data");
        assertNotNull(config, "连接器配置不存在");
        assertEquals(CONFIG_ID, longValue(config.get("id")));
        vendorId = longValue(config.get("vendorId"));
        interfaceId = longValue(config.get("interfaceId"));
        activeConnectorVersionId = longValue(config.get("activeConnectorVersionId"));
        dataTypeCode = stringValue(config.get("dataTypeCode"));
        assertNotNull(vendorId, "连接器配置缺少厂商ID");
        assertNotNull(interfaceId, "连接器配置缺少接口ID");
        assertNotNull(activeConnectorVersionId, "连接器配置缺少活动连接器版本");
        assertNotNull(dataTypeCode, "连接器配置缺少数据类型编码");
        assertEquals("PLUGIN", config.get("runtimeMode"));
        assertEquals("active", config.get("status"));
    }

    private void assertConnectorFact(Map<String, Object> row, boolean expectedSuccess,
                                     boolean expectedCacheHit, String expectedErrorCode,
                                     Long expectedVendorId) {
        assertEquals(interfaceId, longValue(row.get("interfaceId")), "CallRecord接口身份不匹配");
        assertEquals(expectedVendorId, longValue(row.get("vendorId")), "CallRecord实际厂商不匹配");
        assertEquals(EXPECTED_PLUGIN_ID, row.get("pluginId"));
        assertEquals(EXPECTED_PLUGIN_VERSION, row.get("pluginVersion"));
        assertNotNull(row.get("pipelineVersion"));
        assertHash(stringValue(row.get("snapshotHash")), "CallRecord snapshotHash");
        assertHash(stringValue(row.get("integrityHash")), "CallRecord integrityHash");
        assertEquals(expectedSuccess, booleanValue(row.get("success")));
        assertEquals(expectedCacheHit, booleanValue(row.get("cacheHit")));
        if (expectedErrorCode != null) {
            assertEquals(expectedErrorCode, row.get("errorCode"));
        }
    }

    private void awaitBillingRow(Long expectedVendorId, long minimumCalls) {
        String today = LocalDate.now().toString();
        for (int attempt = 0; attempt < 40; attempt++) {
            Response response = getAuthRequest()
                    .queryParam("vendorId", expectedVendorId)
                    .queryParam("startDate", today)
                    .queryParam("endDate", today)
                    .queryParam("page", 1)
                    .queryParam("pageSize", 100)
                    .get("/billing/list");
            verifySuccess(response);
            List<Map<String, Object>> rows = response.jsonPath().getList("data");
            if (rows != null && rows.stream().anyMatch(row ->
                    dataTypeCode.equals(row.get("dataType"))
                            && numberValue(row.get("callCount")) >= minimumCalls
                            && row.get("totalCost") != null)) {
                return;
            }
            pauseForAsyncWrite();
        }
        throw new AssertionError("Billing日聚合未在超时时间内落库: vendor=" + expectedVendorId);
    }

    private Response adminGet(String path) {
        Response response = getAuthRequest().get(path);
        verifySuccess(response);
        return response;
    }

    private String requestBody(String requestId, boolean useCache, String paramsTemplate) {
        try {
            JsonNode params = JSON.readTree(paramsTemplate.replace("${RUN_ID}", RUN_ID));
            assertTrue(params != null && params.isObject(), "连接器验收params必须是JSON对象");
            ObjectNode body = JSON.createObjectNode();
            body.put("requestId", requestId);
            body.put("apiCode", API_CODE);
            body.put("apiVersion", "v1");
            body.put("productCode", PRODUCT_CODE);
            body.put("sceneCode", SCENE_CODE);
            body.put("useCache", useCache);
            body.put("cacheDays", 1);
            body.set("params", params);
            return body.toString();
        } catch (Exception exception) {
            throw new AssertionError("连接器验收参数不是合法JSON", exception);
        }
    }

    private void assertPublicSuccess(Response response, boolean cached, String label) {
        assertEquals(200, response.statusCode(), label + " HTTP状态码不正确");
        assertEquals(200, response.jsonPath().getInt("code"), label + "响应码不正确");
        assertEquals(Boolean.TRUE, response.jsonPath().getBoolean("data.success"), label + "业务失败");
        assertEquals(cached, booleanValue(response.jsonPath().get("data.cached")), label + "缓存标记不正确");
        assertNotNull(platformRequestId(response), label + "缺少platformRequestId");
    }

    private void assertPublicError(Response response, String expectedErrorCode) {
        assertEquals(200, response.statusCode(), "下游错误不应改变Gateway HTTP契约");
        assertEquals(200, response.jsonPath().getInt("code"));
        assertEquals(Boolean.FALSE, response.jsonPath().getBoolean("data.success"));
        assertEquals(expectedErrorCode, response.jsonPath().getString("data.errorCode"));
    }

    private boolean isPublicSuccess(Response response) {
        return response.statusCode() == 200
                && response.jsonPath().getInt("code") == 200
                && booleanValue(response.jsonPath().get("data.success"));
    }

    private String platformRequestId(Response response) {
        String value = response.jsonPath().getString("data.platformRequestId");
        assertNotNull(value, "响应缺少platformRequestId");
        assertFalse(value.isBlank(), "响应缺少platformRequestId");
        return value;
    }

    private static void assertHash(String value, String label) {
        assertNotNull(value, label + "为空");
        assertTrue(value.trim().matches("[0-9a-fA-F]{64}"), label + "不是64位摘要");
    }

    private static String messageOf(Response response) {
        String message = response.jsonPath().getString("msg");
        return message == null ? response.jsonPath().getString("message") : message;
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static long numberValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        return value == null ? 0 : Long.parseLong(String.valueOf(value));
    }

    private static BigDecimal decimalValue(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required connector acceptance setting: " + key);
        }
        return value;
    }

    private static long requiredLong(String key) {
        try {
            return Long.parseLong(required(key));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(key + " must be a number", exception);
        }
    }

    private static int requiredInt(String key) {
        try {
            return Integer.parseInt(required(key));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(key + " must be an integer", exception);
        }
    }

    private static double requiredDouble(String key) {
        try {
            return Double.parseDouble(required(key));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(key + " must be a number", exception);
        }
    }

    private static List<String> csv(String key) {
        List<String> values = List.of(required(key).split(","));
        List<String> normalized = values.stream().map(String::trim).filter(value -> !value.isBlank()).toList();
        if (normalized.isEmpty()) throw new IllegalStateException(key + " must contain at least one value");
        return normalized;
    }

    private static String url(String path) {
        return ACCEPTANCE_GATEWAY_URL.replaceAll("/+$", "") + path;
    }

    private static void pauseForAsyncWrite() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待异步事实落库时被中断", exception);
        }
    }

    private record PublicCall(String requestId, String traceId, Response response) { }

    private record CapacitySample(boolean successful, double durationMs, String platformRequestId) { }
}
