package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 日志管理接口测试
 * 覆盖 2 个接口：列表查询、详情查询
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LogApiTest extends BaseTest {

    private static Long testLogId;

    /**
     * 测试日志列表查询 - 正常场景
     */
    @Test
    @Order(1)
    public void testGetLogList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/log/list");

        verifySuccess(response);
        response.then()
            .body("data", notNullValue());
    }

    /**
     * 测试日志列表查询 - 未授权
     */
    @Test
    @Order(2)
    public void testGetLogList_Unauthorized() {
        given()
            .when()
            .get("/log/list")
            .then()
            .statusCode(401);
    }

    /**
     * 测试日志列表查询 - 带过滤条件
     */
    @Test
    @Order(3)
    public void testGetLogList_WithFilters() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .queryParam("module", "user")
            .queryParam("operation", "login")
            .when()
            .get("/log/list");

        verifySuccess(response);
    }

    /**
     * 测试日志列表查询 - 带时间范围
     */
    @Test
    @Order(4)
    public void testGetLogList_WithDateRange() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .queryParam("startTime", "2024-01-01")
            .queryParam("endTime", "2024-12-31")
            .when()
            .get("/log/list");

        verifySuccess(response);
    }

    /**
     * 测试日志详情查询 - 正常场景
     */
    @Test
    @Order(5)
    public void testGetLogById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/log/1");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
            response.then()
                .body("data", notNullValue());
        } else {
            response.then()
                .statusCode(anyOf(is(404), is(400)));
        }
    }

    /**
     * 测试日志详情查询 - 不存在
     */
    @Test
    @Order(6)
    public void testGetLogById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/log/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试日志详情查询 - 无效ID格式
     */
    @Test
    @Order(7)
    public void testGetLogById_InvalidFormat() {
        Response response = getAuthRequest()
            .when()
            .get("/log/abc");

        response.then()
            .statusCode(400);
    }
}