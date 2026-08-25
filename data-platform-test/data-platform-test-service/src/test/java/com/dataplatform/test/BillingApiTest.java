package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 计费管理接口测试
 * 覆盖 4 个接口：账单列表、账单详情、账单统计、账单导出
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BillingApiTest extends BaseTest {

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

        verifySuccess(response);
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

        verifySuccess(response);
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

        response.then()
            .statusCode(200)
            .contentType(anyOf(containsString("csv"), containsString("excel"), containsString("octet")))
            .body(containsString("billing_date"));
    }

    /**
     * 测试账单导出 - 无权限
     */
    @Test
    @Order(8)
    public void testExportBilling_NoPermission() {
        Response response = given()
            .contentType("application/json")
            .when()
            .get("/billing/export");

        response.then().statusCode(anyOf(is(401), is(403)));
    }
}
