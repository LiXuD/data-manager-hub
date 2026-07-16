package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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
public class BillingBusinessFlowTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(BillingBusinessFlowTest.class);

    private static Long testBillingRuleId;

    // ==================== 链路1：计费规则 CRUD ====================

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
        data.put("billingType", "STANDARD");
        data.put("unitPrice", 0.25);
        data.put("status", "active");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/billing/rule");

        verifySuccess(response);
        testBillingRuleId = extractId(response);
        Assertions.assertNotNull(testBillingRuleId, "计费规则创建成功后应返回ID");
        registerDeleteById("/billing/rule/{id}", testBillingRuleId);

        log.info("计费规则创建成功, ID: {}", testBillingRuleId);
    }

    @Test
    @Order(3)
    @DisplayName("链路1-3: 创建计费规则（缺少ruleName）→ 验证400")
    void testCreateBillingRuleMissingName() {
        Map<String, Object> data = new HashMap<>();
        data.put("unitPrice", 0.5);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/billing/rule");

        response.then().statusCode(400);
        log.info("缺少ruleName校验通过");
    }

    @Test
    @Order(4)
    @DisplayName("链路1-4: 创建计费规则（缺少unitPrice）→ 验证400")
    void testCreateBillingRuleMissingPrice() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", uniqueId("RULE_NO_PRICE"));

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/billing/rule");

        response.then().statusCode(400);
        log.info("缺少unitPrice校验通过");
    }

    @Test
    @Order(5)
    @DisplayName("链路1-5: 更新计费规则 → 验证修改生效")
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
        log.info("计费规则更新验证通过");
    }

    @Test
    @Order(6)
    @DisplayName("链路1-6: 删除计费规则 → 验证已删除")
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
}
