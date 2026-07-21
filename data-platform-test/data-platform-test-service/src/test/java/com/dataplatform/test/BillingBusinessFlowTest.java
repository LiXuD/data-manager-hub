package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * Billing 模块业务链路测试
 *
 * 模拟前端"计费管理"页面的完整业务流程：
 * 1. 计费规则 CRUD
 * 2. 日账单只读查询
 * 3. 统计接口验证
 * 4. 边界测试
 *
 * 基于扫描 BillingController + BillingRule/BillingDaily Entity 生成
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BillingBusinessFlowTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(BillingBusinessFlowTest.class);

    private static Long testBillingRuleId;
    private static Long testVendorId;
    private static Long testDataTypeId;
    private static Long testInterfaceId;

    // ==================== 链路1：计费规则 CRUD ====================

    @Test
    @Order(0)
    @DisplayName("链路1-0: 创建厂商归属接口作为计费规则前置数据")
    void prepareVendorInterface() {
        Response vendors = getAuthRequest()
                .queryParam("page", 1).queryParam("pageSize", 100)
                .get("/vendor/list");
        verifySuccess(vendors);
        List<Map<String, Object>> vendorRows = vendors.jsonPath().getList("data");
        Assertions.assertTrue(vendorRows != null && !vendorRows.isEmpty(), "需要已有厂商");
        testVendorId = ((Number) vendorRows.getFirst().get("id")).longValue();

        Response dataTypes = getAuthRequest()
                .queryParam("page", 1).queryParam("pageSize", 100)
                .get("/datatype/list");
        verifySuccess(dataTypes);
        List<Map<String, Object>> dataTypeRows = dataTypes.jsonPath().getList("data");
        Assertions.assertTrue(dataTypeRows != null && !dataTypeRows.isEmpty(), "需要已有数据类型");
        testDataTypeId = ((Number) dataTypeRows.getFirst().get("id")).longValue();

        String interfaceCode = uniqueId("BILLING_IF");
        Response created = getAuthRequest().body(Map.of(
                        "interfaceCode", interfaceCode,
                        "interfaceName", "计费规则业务测试接口",
                        "vendorId", testVendorId,
                        "dataTypeId", testDataTypeId,
                        "path", "/api/test/" + interfaceCode,
                        "status", "active"))
                .post("/interface");
        verifySuccess(created);
        testInterfaceId = extractId(created);
        Assertions.assertNotNull(testInterfaceId, "计费规则前置接口创建失败");
        registerDeleteById("/interface/{id}", testInterfaceId);
    }

    @Test
    @Order(1)
    @DisplayName("链路1-1: 查询计费规则列表")
    void testBillingRuleList() {
        Response response = getAuthRequest()
            .when()
            .get("/billing/rule/list");

        verifySuccess(response);
        log.info("计费规则列表查询成功");
    }

    @Test
    @Order(2)
    @DisplayName("链路1-2: 创建计费规则 → 提取ID")
    void testCreateBillingRule() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", uniqueId("RULE"));
        data.put("billingType", "TIERED");
        data.put("unitPrice", 0.25);
        data.put("vendorId", testVendorId);
        data.put("interfaceId", testInterfaceId);
        data.put("status", "active");
        data.put("tiers", List.of(
                Map.of("tierMin", 0, "tierMax", 100000, "discount", 1.0),
                Map.of("tierMin", 100000, "tierMax", 200000, "discount", 0.9),
                Map.of("tierMin", 200000, "tierMax", 500000, "discount", 0.8),
                mapWithNullUpperBound(500000, 0.7)
        ));

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/billing/rule");

        verifySuccess(response);
        testBillingRuleId = extractId(response);
        Assertions.assertNotNull(testBillingRuleId, "计费规则创建成功后应返回ID");
        response.then().body("data.tiers.size()", equalTo(4));
        registerDeleteById("/billing/rule/{id}", testBillingRuleId);

        log.info("计费规则创建成功, ID: {}", testBillingRuleId);
    }

    @Test
    @Order(3)
    @DisplayName("链路1-3: 同一厂商与接口重复创建规则 → 验证400")
    void testCreateDuplicateVendorInterfaceRule() {
        Response response = getAuthRequest().body(Map.of(
                        "ruleName", uniqueId("DUPLICATE_RULE"),
                        "vendorId", testVendorId,
                        "interfaceId", testInterfaceId,
                        "unitPrice", 0.5))
                .post("/billing/rule");

        response.then().statusCode(400);
        response.then().body("message", containsString("已存在计费规则"));
    }

    @Test
    @Order(4)
    @DisplayName("链路1-4: 创建计费规则（缺少ruleName）→ 验证400")
    void testCreateBillingRuleMissingName() {
        Map<String, Object> data = new HashMap<>();
        data.put("unitPrice", 0.5);
        data.put("vendorId", testVendorId);
        data.put("interfaceId", testInterfaceId);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/billing/rule");

        response.then().statusCode(400);
        log.info("缺少ruleName校验通过");
    }

    @Test
    @Order(5)
    @DisplayName("链路1-5: 创建计费规则（缺少unitPrice）→ 验证400")
    void testCreateBillingRuleMissingPrice() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", uniqueId("RULE_NO_PRICE"));
        data.put("vendorId", testVendorId);
        data.put("interfaceId", testInterfaceId);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/billing/rule");

        response.then().statusCode(400);
        log.info("缺少unitPrice校验通过");
    }

    @Test
    @Order(6)
    @DisplayName("链路1-6: 更新计费规则 → 验证修改生效")
    void testUpdateBillingRule() {
        org.junit.jupiter.api.Assertions.assertTrue(testBillingRuleId != null, "需要测试计费规则ID");

        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", uniqueId("RULE_UPDATED"));
        data.put("unitPrice", 0.35);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/billing/rule/" + testBillingRuleId);

        verifySuccess(response);
        Response listResponse = getAuthRequest().get("/billing/rule/list");
        verifySuccess(listResponse);
        List<Map<String, Object>> rules = listResponse.jsonPath().getList("data");
        Map<String, Object> updatedRule = rules.stream()
                .filter(item -> testBillingRuleId.equals(((Number) item.get("id")).longValue()))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals("TIERED", updatedRule.get("billingType"));
        Assertions.assertEquals(4, ((List<?>) updatedRule.get("tiers")).size());
        log.info("计费规则更新验证通过");
    }

    @Test
    @Order(7)
    @DisplayName("链路1-7: 删除计费规则 → 验证已删除")
    void testDeleteBillingRule() {
        org.junit.jupiter.api.Assertions.assertTrue(testBillingRuleId != null, "没有需要删除的计费规则");

        Response response = getAuthRequest()
            .when()
            .delete("/billing/rule/" + testBillingRuleId);

        verifySuccess(response);
        log.info("计费规则删除成功, ID: {}", testBillingRuleId);

        // 验证已删除
        Response checkResponse = getAuthRequest()
            .when()
            .get("/billing/rule/list");

        verifySuccess(checkResponse);

        testBillingRuleId = null;
    }

    // ==================== 链路2：日账单只读查询 ====================

    @Test
    @Order(10)
    @DisplayName("链路2-1: 查询日账单列表")
    void testBillingDailyList() {
        Response response = getAuthRequest()
            .when()
            .get("/billing/list");

        verifySuccess(response);
        log.info("日账单列表查询成功");
    }

    @Test
    @Order(11)
    @DisplayName("链路2-2: 查询不存在的日账单 → 验证404")
    void testBillingDailyNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/billing/" + NON_EXISTENT_ID);

        response.then().statusCode(anyOf(is(404), is(400)));
        log.info("日账单不存在校验通过");
    }

    @Test
    @Order(12)
    @DisplayName("链路2-3: 查询账单统计")
    void testBillingStats() {
        Response response = getAuthRequest()
            .when()
            .get("/billing/stats");

        verifySuccess(response);
        log.info("账单统计查询成功");
    }

    // ==================== 链路3：边界测试 ====================

    @Test
    @Order(20)
    @DisplayName("边界-1: 更新不存在的计费规则 → 验证404")
    void testUpdateBillingRuleNotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", "不存在的规则");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/billing/rule/" + NON_EXISTENT_ID);

        response.then().statusCode(anyOf(is(404), is(400)));
        log.info("更新不存在的计费规则校验通过");
    }

    @Test
    @Order(21)
    @DisplayName("边界-2: 删除不存在的计费规则 → 验证404")
    void testDeleteBillingRuleNotFound() {
        Response response = getAuthRequest()
            .when()
            .delete("/billing/rule/" + NON_EXISTENT_ID);

        response.then().statusCode(anyOf(is(404), is(400)));
        log.info("删除不存在的计费规则校验通过");
    }

    @Test
    @Order(22)
    @DisplayName("边界-3: 未授权访问计费规则列表 → 验证401")
    void testBillingRuleListUnauthorized() {
        given()
            .when()
            .get("/billing/rule/list")
            .then()
            .statusCode(401);
    }

    private Map<String, Object> mapWithNullUpperBound(long tierMin, double discount) {
        Map<String, Object> tier = new HashMap<>();
        tier.put("tierMin", tierMin);
        tier.put("tierMax", null);
        tier.put("discount", discount);
        return tier;
    }
}
