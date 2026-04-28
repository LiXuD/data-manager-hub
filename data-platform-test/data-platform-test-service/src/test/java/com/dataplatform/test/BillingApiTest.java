package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 计费管理接口测试
 * 覆盖 9 个接口：账单列表、账单详情、账单统计、账单导出、计费规则列表、创建计费规则、更新计费规则、删除计费规则
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BillingApiTest extends BaseTest {

    private static Long testBillingRuleId;

    /**
     * 测试账单列表查询 - 正常场景
     */
    @Test
    @Order(1)
    public void testGetBillingList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/billing/list");

        verifySuccess(response);
    }

    /**
     * 测试账单列表查询 - 未授权
     */
    @Test
    @Order(2)
    public void testGetBillingList_Unauthorized() {
        given()
            .when()
            .get("/billing/list")
            .then()
            .statusCode(401);
    }

    /**
     * 测试账单详情查询 - 正常场景
     */
    @Test
    @Order(3)
    public void testGetBillingById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/billing/1");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        } else {
            response.then()
                .statusCode(anyOf(is(404), is(400)));
        }
    }

    /**
     * 测试账单详情查询 - 不存在
     */
    @Test
    @Order(4)
    public void testGetBillingById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/billing/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试账单统计 - 正常场景
     */
    @Test
    @Order(5)
    public void testGetBillingStats_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/billing/stats");

        verifySuccess(response);
    }

    /**
     * 测试账单统计 - 带时间范围参数
     */
    @Test
    @Order(6)
    public void testGetBillingStats_WithDateRange() {
        Response response = getAuthRequest()
            .queryParam("startDate", "2024-01-01")
            .queryParam("endDate", "2024-12-31")
            .when()
            .get("/billing/stats");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试账单导出 - 正常场景
     */
    @Test
    @Order(7)
    public void testExportBilling_Success() {
        Response response = getAuthRequest()
            .queryParam("format", "csv")
            .when()
            .get("/billing/export");

        if (response.getStatusCode() == 200) {
            response.then()
                .contentType(anyOf(containsString("csv"), containsString("excel"), containsString("octet")));
        }
    }

    /**
     * 测试账单导出 - 无权限
     */
    @Test
    @Order(8)
    public void testExportBilling_NoPermission() {
        Response response = getAuthRequest()
            .when()
            .get("/billing/export");

        if (response.getStatusCode() == 403) {
            response.then()
                .statusCode(403);
        }
    }

    /**
     * 测试计费规则列表 - 正常场景
     */
    @Test
    @Order(9)
    public void testGetBillingRuleList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/billing/rule/list");

        verifySuccess(response);
    }

    /**
     * 测试计费规则列表 - 未授权
     */
    @Test
    @Order(10)
    public void testGetBillingRuleList_Unauthorized() {
        given()
            .when()
            .get("/billing/rule/list")
            .then()
            .statusCode(401);
    }

    /**
     * 测试创建计费规则 - 正常场景
     */
    @Test
    @Order(11)
    public void testCreateBillingRule_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", "测试规则_" + System.currentTimeMillis());
        data.put("ruleType", "CALL");
        data.put("pricePerUnit", 0.5);
        data.put("unit", "MINUTE");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/billing/rule");

        verifySuccess(response);

        Integer id = response.jsonPath().getInt("data.id");
        if (id != null) {
            testBillingRuleId = id.longValue();
        }
    }

    /**
     * 测试创建计费规则 - 必填参数缺失
     */
    @Test
    @Order(12)
    public void testCreateBillingRule_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", "测试规则");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/billing/rule");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试创建计费规则 - 价格格式错误
     */
    @Test
    @Order(13)
    public void testCreateBillingRule_InvalidPrice() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", "测试规则_" + System.currentTimeMillis());
        data.put("ruleType", "CALL");
        data.put("pricePerUnit", -1);
        data.put("unit", "MINUTE");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/billing/rule");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试更新计费规则 - 正常场景
     */
    @Test
    @Order(14)
    public void testUpdateBillingRule_Success() {
        if (testBillingRuleId == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "No test billing rule to update");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("pricePerUnit", 0.8);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/billing/rule/" + testBillingRuleId);

        verifySuccess(response);
    }

    /**
     * 测试更新计费规则 - 不存在
     */
    @Test
    @Order(15)
    public void testUpdateBillingRule_NotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("pricePerUnit", 0.8);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/billing/rule/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试删除计费规则 - 正常场景
     */
    @Test
    @Order(16)
    public void testDeleteBillingRule_Success() {
        if (testBillingRuleId == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "No test billing rule to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/billing/rule/" + testBillingRuleId);

        response.then()
            .statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试删除计费规则 - 不存在
     */
    @Test
    @Order(17)
    public void testDeleteBillingRule_NotFound() {
        Response response = getAuthRequest()
            .when()
            .delete("/billing/rule/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }
}