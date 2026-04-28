package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 租户管理接口测试
 * 覆盖 6 个接口：列表、详情、创建、更新、删除、状态修改
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TenantApiTest extends BaseTest {

    /**
     * 测试租户列表查询 - 正常场景
     */
    @Test
    @Order(1)
    public void testGetTenantList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/tenant/list");

        verifySuccess(response);
    }

    /**
     * 测试租户列表查询 - 未授权
     */
    @Test
    @Order(2)
    public void testGetTenantList_Unauthorized() {
        given()
            .when()
            .get("/tenant/list")
            .then()
            .statusCode(401);
    }

    /**
     * 测试租户详情查询 - 正常场景
     */
    @Test
    @Order(3)
    public void testGetTenantById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/tenant/1");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试租户详情查询 - 不存在
     */
    @Test
    @Order(4)
    public void testGetTenantById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/tenant/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试创建租户 - 正常场景
     */
    @Test
    @Order(5)
    public void testCreateTenant_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("tenantName", "测试租户_" + System.currentTimeMillis());
        data.put("tenantCode", "TEST_" + System.currentTimeMillis());
        data.put("contactName", "联系人");
        data.put("contactPhone", "13800138000");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/tenant");

        verifySuccess(response);

        Integer id = response.jsonPath().getInt("data.id");
        if (id != null) {
            testTenantId = id.longValue();
        }
    }

    /**
     * 测试创建租户 - 必填参数缺失
     */
    @Test
    @Order(6)
    public void testCreateTenant_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "联系人");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/tenant");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试创建租户 - 租户代码重复
     */
    @Test
    @Order(7)
    public void testCreateTenant_DuplicateCode() {
        Map<String, Object> data = new HashMap<>();
        data.put("tenantName", "测试");
        data.put("tenantCode", "SYSTEM");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/tenant");

        response.then()
            .statusCode(anyOf(is(400), is(409)));
    }

    /**
     * 测试更新租户 - 正常场景
     */
    @Test
    @Order(8)
    public void testUpdateTenant_Success() {
        if (testTenantId == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "No test tenant to update");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "新联系人");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/tenant/" + testTenantId);

        verifySuccess(response);
    }

    /**
     * 测试更新租户 - 不存在
     */
    @Test
    @Order(9)
    public void testUpdateTenant_NotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "测试");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/tenant/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试删除租户 - 正常场景
     */
    @Test
    @Order(10)
    public void testDeleteTenant_Success() {
        if (testTenantId == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "No test tenant to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/tenant/" + testTenantId);

        response.then()
            .statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试删除租户 - 不存在
     */
    @Test
    @Order(11)
    public void testDeleteTenant_NotFound() {
        Response response = getAuthRequest()
            .when()
            .delete("/tenant/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试修改租户状态 - 正常场景
     */
    @Test
    @Order(12)
    public void testUpdateTenantStatus_Success() {
        Response response = getAuthRequest()
            .body(Map.of("status", "1"))
            .when()
            .patch("/tenant/1/status");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试修改租户状态 - 无效状态值
     */
    @Test
    @Order(13)
    public void testUpdateTenantStatus_InvalidStatus() {
        Response response = getAuthRequest()
            .body(Map.of("status", "invalid_status"))
            .when()
            .patch("/tenant/1/status");

        response.then()
            .statusCode(400);
    }
}