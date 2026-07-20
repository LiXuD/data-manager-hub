package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 通话记录接口测试
 * 覆盖 5 个接口：列表、详情、统计、查询、导出
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CallApiTest extends BaseTest {

    private static Long testCallRecordId;

    /**
     * 测试通话记录列表查询 - 正常场景
     */
    @Test
    @Order(1)
    public void testGetCallRecordList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/call-record/list");

        verifySuccess(response);
    }

    /**
     * 测试通话记录列表查询 - 未授权
     */
    @Test
    @Order(2)
    public void testGetCallRecordList_Unauthorized() {
        given()
            .when()
            .get("/call-record/list")
            .then()
            .statusCode(401);
    }

    /**
     * 测试通话记录列表查询 - 带分页参数
     */
    @Test
    @Order(3)
    public void testGetCallRecordList_WithPagination() {
        Response response = getAuthRequest()
            .queryParam("page", 2)
            .queryParam("pageSize", 20)
            .when()
            .get("/call-record/list");

        verifySuccess(response);
    }

    /**
     * 测试通话记录详情查询 - 正常场景
     */
    @Test
    @Order(4)
    public void testGetCallRecordById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/call-record/1");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试通话记录详情查询 - 不存在
     */
    @Test
    @Order(5)
    public void testGetCallRecordById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/call-record/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试通话记录统计 - 正常场景
     */
    @Test
    @Order(6)
    public void testGetCallRecordStats_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/call-record/stats");

        verifySuccess(response);
    }

    /**
     * 测试通话记录统计 - 带时间范围
     */
    @Test
    @Order(7)
    public void testGetCallRecordStats_WithDateRange() {
        Response response = getAuthRequest()
            .queryParam("startDate", "2024-01-01")
            .queryParam("endDate", "2024-12-31")
            .when()
            .get("/call-record/stats");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试通话记录查询 - 正常场景
     */
    @Test
    @Order(8)
    public void testQueryCallRecord_Success() {
        Map<String, Object> queryData = new HashMap<>();
        queryData.put("callerId", 1);
        queryData.put("page", 1);
        queryData.put("pageSize", 10);

        Response response = getAuthRequest()
            .body(queryData)
            .when()
            .post("/call-record/query");

        verifySuccess(response);
    }

    /**
     * 测试通话记录查询 - 条件查询
     */
    @Test
    @Order(9)
    public void testQueryCallRecord_WithConditions() {
        Map<String, Object> queryData = new HashMap<>();
        queryData.put("phoneNumber", "13800138000");
        queryData.put("startTime", "2024-01-01 00:00:00");
        queryData.put("endTime", "2024-12-31 23:59:59");
        queryData.put("page", 1);
        queryData.put("pageSize", 10);

        Response response = getAuthRequest()
            .body(queryData)
            .when()
            .post("/call-record/query");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试通话记录查询 - 无效参数
     */
    @Test
    @Order(10)
    public void testQueryCallRecord_InvalidParams() {
        Map<String, Object> queryData = new HashMap<>();
        queryData.put("page", -1);
        queryData.put("pageSize", 1000);

        Response response = getAuthRequest()
            .body(queryData)
            .when()
            .post("/call-record/query");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试通话记录导出 - 正常场景
     */
    @Test
    @Order(11)
    public void testExportCallRecord_Success() {
        Response response = getAuthRequest()
            .queryParam("format", "csv")
            .when()
            .get("/call-record/export");

        verifySuccess(response);
        {
            response.then()
                .contentType(anyOf(containsString("csv"), containsString("excel"), containsString("octet")));
        }
    }

    /**
     * 测试通话记录导出 - 无权限
     */
    @Test
    @Order(12)
    public void testExportCallRecord_NoPermission() {
        Response response = given()
            .contentType("application/json")
            .when()
            .get("/call-record/export");

        response.then().statusCode(anyOf(is(401), is(403)));
    }

    /**
     * 测试通话记录导出 - 带过滤条件
     */
    @Test
    @Order(13)
    public void testExportCallRecord_WithFilter() {
        Response response = getAuthRequest()
            .queryParam("format", "csv")
            .queryParam("startDate", "2024-01-01")
            .queryParam("endDate", "2024-12-31")
            .when()
            .get("/call-record/export");

        verifySuccess(response);
        {
            response.then()
                .contentType(anyOf(containsString("csv"), containsString("excel"), containsString("octet")));
        }
    }
}
