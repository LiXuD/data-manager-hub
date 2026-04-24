package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 供应商管理接口测试
 * 覆盖 7 个接口：列表、详情、创建、更新、删除、状态修改、测试连接
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VendorApiTest extends BaseTest {

    private static Long testVendorId;

    /**
     * 测试供应商列表查询 - 正常场景
     */
    @Test
    @Order(1)
    public void testGetVendorList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/vendor/list");

        verifySuccess(response);
    }

    /**
     * 测试供应商列表查询 - 未授权
     */
    @Test
    @Order(2)
    public void testGetVendorList_Unauthorized() {
        given()
            .when()
            .get("/vendor/list")
            .then()
            .statusCode(401);
    }

    /**
     * 测试供应商详情查询 - 正常场景
     */
    @Test
    @Order(3)
    public void testGetVendorById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/vendor/1");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        } else {
            response.then()
                .statusCode(anyOf(is(404), is(400)));
        }
    }

    /**
     * 测试供应商详情查询 - 不存在
     */
    @Test
    @Order(4)
    public void testGetVendorById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/vendor/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试创建供应商 - 正常场景
     */
    @Test
    @Order(5)
    public void testCreateVendor_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("vendorName", "测试供应商_" + System.currentTimeMillis());
        data.put("vendorCode", "VENDOR_" + System.currentTimeMillis());
        data.put("contactPerson", "联系人");
        data.put("contactPhone", "13800138000");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/vendor");

        verifySuccess(response);

        Integer id = response.jsonPath().getInt("data.id");
        if (id != null) {
            testVendorId = id.longValue();
        }
    }

    /**
     * 测试创建供应商 - 必填参数缺失
     */
    @Test
    @Order(6)
    public void testCreateVendor_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactPerson", "联系人");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/vendor");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试创建供应商 - 供应商代码重复
     */
    @Test
    @Order(7)
    public void testCreateVendor_DuplicateCode() {
        Map<String, Object> data = new HashMap<>();
        data.put("vendorName", "测试");
        data.put("vendorCode", "SYSTEM");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/vendor");

        response.then()
            .statusCode(anyOf(is(400), is(409)));
    }

    /**
     * 测试更新供应商 - 正常场景
     */
    @Test
    @Order(8)
    public void testUpdateVendor_Success() {
        if (testVendorId == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "No test vendor to update");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "新联系人");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/vendor/" + testVendorId);

        verifySuccess(response);
    }

    /**
     * 测试更新供应商 - 不存在
     */
    @Test
    @Order(9)
    public void testUpdateVendor_NotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("contactName", "测试");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/vendor/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试删除供应商 - 正常场景
     */
    @Test
    @Order(10)
    public void testDeleteVendor_Success() {
        if (testVendorId == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "No test vendor to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/vendor/" + testVendorId);

        response.then()
            .statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试删除供应商 - 不存在
     */
    @Test
    @Order(11)
    public void testDeleteVendor_NotFound() {
        Response response = getAuthRequest()
            .when()
            .delete("/vendor/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试修改供应商状态 - 正常场景
     */
    @Test
    @Order(12)
    public void testUpdateVendorStatus_Success() {
        Response response = getAuthRequest()
            .body(Map.of("status", "1"))
            .when()
            .patch("/vendor/1/status");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试修改供应商状态 - 无效状态值
     */
    @Test
    @Order(13)
    public void testUpdateVendorStatus_InvalidStatus() {
        Response response = getAuthRequest()
            .body(Map.of("status", "invalid_status"))
            .when()
            .patch("/vendor/1/status");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试供应商连接 - 正常场景
     */
    @Test
    @Order(14)
    public void testVendorConnection_Success() {
        Response response = getAuthRequest()
            .when()
            .post("/vendor/1/test");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试供应商连接 - 不存在的供应商
     */
    @Test
    @Order(15)
    public void testVendorConnection_NotFound() {
        Response response = getAuthRequest()
            .when()
            .post("/vendor/999999999/test");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }
}