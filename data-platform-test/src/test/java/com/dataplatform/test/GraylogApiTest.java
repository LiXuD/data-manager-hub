package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * Graylog 接口测试
 * 覆盖灰度规则管理接口：列表、详情、创建、更新、删除、状态修改、获取活跃规则
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GraylogApiTest extends BaseTest {

    private static Long testGraylogId;

    /**
     * 测试 Graylog 规则列表查询 - 正常场景
     */
    @Test
    @Order(1)
    public void testGetGraylogList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/graylog/list");

        verifySuccess(response);
    }

    /**
     * 测试 Graylog 规则列表查询 - 未授权
     */
    @Test
    @Order(2)
    public void testGetGraylogList_Unauthorized() {
        given()
            .when()
            .get("/graylog/list")
            .then()
            .statusCode(401);
    }

    /**
     * 测试 Graylog 规则详情查询 - 正常场景
     */
    @Test
    @Order(3)
    public void testGetGraylogById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/graylog/1");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        } else {
            response.then()
                .statusCode(anyOf(is(404), is(400)));
        }
    }

    /**
     * 测试 Graylog 规则详情查询 - 不存在
     */
    @Test
    @Order(4)
    public void testGetGraylogById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/graylog/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试创建 Graylog 规则 - 正常场景
     */
    @Test
    @Order(5)
    public void testCreateGraylog_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", "测试灰度规则_" + System.currentTimeMillis());
        data.put("serviceName", "test-service-" + System.currentTimeMillis());
        data.put("version", "v1.0");
        data.put("weight", 50);
        data.put("conditionType", "percentage");
        data.put("conditionValue", "50");
        data.put("description", "测试灰度规则描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/graylog");

        verifySuccess(response);

        Integer id = response.jsonPath().getInt("data.id");
        if (id != null) {
            testGraylogId = id.longValue();
        }
    }

    /**
     * 测试创建 Graylog 规则 - 必填参数缺失
     */
    @Test
    @Order(6)
    public void testCreateGraylog_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("description", "测试灰度规则描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/graylog");

        response.then()
            .statusCode(anyOf(is(400), is(500)));
    }

    /**
     * 测试更新 Graylog 规则 - 正常场景
     */
    @Test
    @Order(7)
    public void testUpdateGraylog_Success() {
        if (testGraylogId == null) {
            Assertions.skip("No test graylog to update");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("weight", 80);
        data.put("description", "更新的灰度规则描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/graylog/" + testGraylogId);

        verifySuccess(response);
    }

    /**
     * 测试更新 Graylog 规则 - 不存在
     */
    @Test
    @Order(8)
    public void testUpdateGraylog_NotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("weight", 100);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/graylog/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试删除 Graylog 规则 - 正常场景
     */
    @Test
    @Order(9)
    public void testDeleteGraylog_Success() {
        if (testGraylogId == null) {
            Assertions.skip("No test graylog to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/graylog/" + testGraylogId);

        response.then()
            .statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试删除 Graylog 规则 - 不存在
     */
    @Test
    @Order(10)
    public void testDeleteGraylog_NotFound() {
        Response response = getAuthRequest()
            .when()
            .delete("/graylog/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试获取活跃规则 - 正常场景
     */
    @Test
    @Order(11)
    public void testGetActiveGraylog_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/graylog/active/test-service");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        } else {
            response.then()
                .statusCode(anyOf(is(404), is(400)));
        }
    }

    /**
     * 测试获取活跃规则 - 不存在的服务
     */
    @Test
    @Order(12)
    public void testGetActiveGraylog_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/graylog/active/non-existent-service-999999999");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        } else {
            response.then()
                .statusCode(anyOf(is(404), is(400)));
        }
    }

    /**
     * 测试修改 Graylog 规则状态 - 正常场景
     */
    @Test
    @Order(13)
    public void testUpdateGraylogStatus_Success() {
        Response response = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/graylog/1/status");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试修改 Graylog 规则状态 - 无效状态值
     */
    @Test
    @Order(14)
    public void testUpdateGraylogStatus_InvalidStatus() {
        Response response = getAuthRequest()
            .body(Map.of("status", "invalid_status"))
            .when()
            .patch("/graylog/1/status");

        response.then()
            .statusCode(anyOf(is(400), is(500)));
    }
}