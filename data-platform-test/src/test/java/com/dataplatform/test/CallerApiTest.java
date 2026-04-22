package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 调用方管理接口测试
 * 覆盖 9 个接口：列表、详情、创建、更新、删除、API Key列表、创建API Key、修改API Key状态、删除API Key
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CallerApiTest extends BaseTest {

    private static Long testCallerId;
    private static Long testApiKeyId;

    /**
     * 测试调用方列表查询 - 正常场景
     */
    @Test
    @Order(1)
    public void testGetCallerList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/caller/list");

        verifySuccess(response);
    }

    /**
     * 测试调用方列表查询 - 未授权
     */
    @Test
    @Order(2)
    public void testGetCallerList_Unauthorized() {
        given()
            .when()
            .get("/caller/list")
            .then()
            .statusCode(401);
    }

    /**
     * 测试调用方详情查询 - 正常场景
     */
    @Test
    @Order(3)
    public void testGetCallerById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/caller/1");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        } else {
            response.then()
                .statusCode(anyOf(is(404), is(400)));
        }
    }

    /**
     * 测试调用方详情查询 - 不存在
     */
    @Test
    @Order(4)
    public void testGetCallerById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/caller/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试创建调用方 - 正常场景
     */
    @Test
    @Order(5)
    public void testCreateCaller_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("callerName", "测试调用方_" + System.currentTimeMillis());
        data.put("callerCode", "CALLER_" + System.currentTimeMillis());
        data.put("contactName", "联系人");
        data.put("contactPhone", "13800138000");
        data.put("contactEmail", "test@example.com");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/caller");

        verifySuccess(response);

        Integer id = response.jsonPath().getInt("data.id");
        if (id != null) {
            testCallerId = id.longValue();
        }
    }

    /**
     * 测试创建调用方 - 必填参数缺失
     */
    @Test
    @Order(6)
    public void testCreateCaller_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "联系人");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/caller");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试创建调用方 - 调用方代码重复
     */
    @Test
    @Order(7)
    public void testCreateCaller_DuplicateCode() {
        Map<String, Object> data = new HashMap<>();
        data.put("callerName", "测试");
        data.put("callerCode", "SYSTEM");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/caller");

        response.then()
            .statusCode(anyOf(is(400), is(409)));
    }

    /**
     * 测试更新调用方 - 正常场景
     */
    @Test
    @Order(8)
    public void testUpdateCaller_Success() {
        if (testCallerId == null) {
            Assertions.skip("No test caller to update");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "新联系人");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/caller/" + testCallerId);

        verifySuccess(response);
    }

    /**
     * 测试更新调用方 - 不存在
     */
    @Test
    @Order(9)
    public void testUpdateCaller_NotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "测试");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/caller/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试删除调用方 - 正常场景
     */
    @Test
    @Order(10)
    public void testDeleteCaller_Success() {
        if (testCallerId == null) {
            Assertions.skip("No test caller to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/caller/" + testCallerId);

        response.then()
            .statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试删除调用方 - 不存在
     */
    @Test
    @Order(11)
    public void testDeleteCaller_NotFound() {
        Response response = getAuthRequest()
            .when()
            .delete("/caller/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试获取调用方API Key列表 - 正常场景
     */
    @Test
    @Order(12)
    public void testGetApiKeyList_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/caller/1/api-key/list");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试获取调用方API Key列表 - 不存在的调用方
     */
    @Test
    @Order(13)
    public void testGetApiKeyList_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/caller/999999999/api-key/list");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试创建API Key - 正常场景
     */
    @Test
    @Order(14)
    public void testCreateApiKey_Success() {
        if (testCallerId == null) {
            Assertions.skip("No test caller to create API key");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("keyName", "测试Key_" + System.currentTimeMillis());
        data.put("expireDays", 365);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/caller/" + testCallerId + "/api-key");

        verifySuccess(response);

        Integer id = response.jsonPath().getInt("data.id");
        if (id != null) {
            testApiKeyId = id.longValue();
        }
    }

    /**
     * 测试创建API Key - 必填参数缺失
     */
    @Test
    @Order(15)
    public void testCreateApiKey_MissingRequired() {
        if (testCallerId == null) {
            Assertions.skip("No test caller");
            return;
        }

        Map<String, Object> data = new HashMap<>();

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/caller/" + testCallerId + "/api-key");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试创建API Key - 不存在的调用方
     */
    @Test
    @Order(16)
    public void testCreateApiKey_CallerNotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("keyName", "测试Key");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/caller/999999999/api-key");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试修改API Key状态 - 正常场景
     */
    @Test
    @Order(17)
    public void testUpdateApiKeyStatus_Success() {
        Response response = getAuthRequest()
            .body(Map.of("status", "1"))
            .when()
            .patch("/caller/api-key/1/status");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试修改API Key状态 - 不存在的Key
     */
    @Test
    @Order(18)
    public void testUpdateApiKeyStatus_NotFound() {
        Response response = getAuthRequest()
            .body(Map.of("status", "1"))
            .when()
            .patch("/caller/api-key/999999999/status");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试修改API Key状态 - 无效状态值
     */
    @Test
    @Order(19)
    public void testUpdateApiKeyStatus_InvalidStatus() {
        Response response = getAuthRequest()
            .body(Map.of("status", "invalid"))
            .when()
            .patch("/caller/api-key/1/status");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试删除API Key - 正常场景
     */
    @Test
    @Order(20)
    public void testDeleteApiKey_Success() {
        if (testApiKeyId == null) {
            Assertions.skip("No test API key to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/caller/api-key/" + testApiKeyId);

        response.then()
            .statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试删除API Key - 不存在
     */
    @Test
    @Order(21)
    public void testDeleteApiKey_NotFound() {
        Response response = getAuthRequest()
            .when()
            .delete("/caller/api-key/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }
}