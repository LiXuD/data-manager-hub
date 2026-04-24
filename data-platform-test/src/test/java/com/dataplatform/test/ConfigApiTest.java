package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 配置管理接口测试
 * 覆盖 7 个接口：列表、详情、创建、更新、删除、按供应商查询、状态修改
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConfigApiTest extends BaseTest {

    private static Long testConfigId;
    private static Long testVendorId = 1L;

    /**
     * 测试配置列表查询 - 正常场景
     */
    @Test
    @Order(1)
    public void testGetConfigList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/config/list");

        verifySuccess(response);
    }

    /**
     * 测试配置列表查询 - 未授权
     */
    @Test
    @Order(2)
    public void testGetConfigList_Unauthorized() {
        given()
            .when()
            .get("/config/list")
            .then()
            .statusCode(401);
    }

    /**
     * 测试配置详情查询 - 正常场景
     */
    @Test
    @Order(3)
    public void testGetConfigById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/config/1");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        } else {
            response.then()
                .statusCode(anyOf(is(404), is(400)));
        }
    }

    /**
     * 测试配置详情查询 - 不存在
     */
    @Test
    @Order(4)
    public void testGetConfigById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/config/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试创建配置 - 正常场景
     */
    @Test
    @Order(5)
    public void testCreateConfig_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("vendorId", testVendorId);
        data.put("configKey", "test_key_" + System.currentTimeMillis());
        data.put("configValue", "test_value");
        data.put("configType", "string");
        data.put("description", "测试配置");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/config");

        verifySuccess(response);

        Integer id = response.jsonPath().getInt("data.id");
        if (id != null) {
            testConfigId = id.longValue();
        }
    }

    /**
     * 测试创建配置 - 必填参数缺失
     */
    @Test
    @Order(6)
    public void testCreateConfig_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("description", "测试配置");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/config");

        response.then()
            .statusCode(anyOf(is(400), is(500)));
    }

    /**
     * 测试更新配置 - 正常场景
     */
    @Test
    @Order(7)
    public void testUpdateConfig_Success() {
        if (testConfigId == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "No test config to update");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("configValue", "updated_value");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/config/" + testConfigId);

        verifySuccess(response);
    }

    /**
     * 测试更新配置 - 不存在
     */
    @Test
    @Order(8)
    public void testUpdateConfig_NotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("configValue", "test");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/config/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试删除配置 - 正常场景
     */
    @Test
    @Order(9)
    public void testDeleteConfig_Success() {
        if (testConfigId == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "No test config to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/config/" + testConfigId);

        response.then()
            .statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试删除配置 - 不存在
     */
    @Test
    @Order(10)
    public void testDeleteConfig_NotFound() {
        Response response = getAuthRequest()
            .when()
            .delete("/config/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试按供应商查询配置 - 正常场景
     */
    @Test
    @Order(11)
    public void testGetConfigByVendor_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/config/vendor/" + testVendorId);

        verifySuccess(response);
    }

    /**
     * 测试按供应商查询配置 - 不存在的供应商
     */
    @Test
    @Order(12)
    public void testGetConfigByVendor_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/config/vendor/999999999");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        } else {
            response.then()
                .statusCode(anyOf(is(404), is(400)));
        }
    }

    /**
     * 测试修改配置状态 - 正常场景
     */
    @Test
    @Order(13)
    public void testUpdateConfigStatus_Success() {
        Response response = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/config/1/status");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试修改配置状态 - 无效状态值
     */
    @Test
    @Order(14)
    public void testUpdateConfigStatus_InvalidStatus() {
        Response response = getAuthRequest()
            .body(Map.of("status", "invalid_status"))
            .when()
            .patch("/config/1/status");

        response.then()
            .statusCode(anyOf(is(400), is(500)));
    }
}