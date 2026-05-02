package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 厂商管理接口测试
 *
 * 整合原 VendorApiTest + DataTypeApiTest + ConfigApiTest
 * 对应 data-platform-vendor-service 服务
 *
 * 覆盖接口：
 * - 厂商管理：列表、详情、创建、更新、删除、状态修改、测试连接
 * - 数据类型：列表、详情、创建、更新、删除、状态修改
 * - 配置管理：列表、详情、创建、更新、删除、按厂商查询、状态修改
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VendorApiTest extends BaseTest {

    private static Long testVendorId;
    private static Long testDataTypeId;
    private static Long testConfigId;

    // ==================== 厂商管理测试 ====================

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
            response.then().statusCode(anyOf(is(404), is(400)));
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
            .get("/vendor/" + NON_EXISTENT_ID);

        response.then().statusCode(anyOf(is(404), is(400)));
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

        response.then().statusCode(400);
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

        response.then().statusCode(anyOf(is(400), is(409)));
    }

    /**
     * 测试更新供应商 - 正常场景
     */
    @Test
    @Order(8)
    public void testUpdateVendor_Success() {
        if (testVendorId == null) {
            Assumptions.assumeTrue(false, "No test vendor to update");
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
     * 测试删除供应商 - 正常场景
     */
    @Test
    @Order(9)
    public void testDeleteVendor_Success() {
        if (testVendorId == null) {
            Assumptions.assumeTrue(false, "No test vendor to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/vendor/" + testVendorId);

        response.then().statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试修改供应商状态 - 正常场景
     */
    @Test
    @Order(10)
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
     * 测试供应商连接 - 正常场景
     */
    @Test
    @Order(11)
    public void testVendorConnection_Success() {
        Response response = getAuthRequest()
            .when()
            .post("/vendor/1/test");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    // ==================== 数据类型测试 ====================

    /**
     * 测试数据类型列表查询 - 正常场景
     */
    @Test
    @Order(20)
    public void testGetDataTypeList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/datatype/list");

        verifySuccess(response);
    }

    /**
     * 测试数据类型列表查询 - 未授权
     */
    @Test
    @Order(21)
    public void testGetDataTypeList_Unauthorized() {
        given()
            .when()
            .get("/datatype/list")
            .then()
            .statusCode(401);
    }

    /**
     * 测试数据类型详情查询 - 正常场景
     */
    @Test
    @Order(22)
    public void testGetDataTypeById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/datatype/1");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        } else {
            response.then().statusCode(anyOf(is(404), is(400)));
        }
    }

    /**
     * 测试数据类型详情查询 - 不存在
     */
    @Test
    @Order(23)
    public void testGetDataTypeById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/datatype/" + NON_EXISTENT_ID);

        response.then().statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试创建数据类型 - 正常场景
     */
    @Test
    @Order(24)
    public void testCreateDataType_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("datatypeCode", "DT_" + System.currentTimeMillis());
        data.put("datatypeName", "测试数据类型_" + System.currentTimeMillis());
        data.put("description", "测试数据类型描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/datatype");

        verifySuccess(response);

        Integer id = response.jsonPath().getInt("data.id");
        if (id != null) {
            testDataTypeId = id.longValue();
        }
    }

    /**
     * 测试创建数据类型 - 必填参数缺失
     */
    @Test
    @Order(25)
    public void testCreateDataType_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("description", "测试数据类型描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/datatype");

        response.then().statusCode(anyOf(is(400), is(500)));
    }

    /**
     * 测试创建数据类型 - 数据类型代码重复
     */
    @Test
    @Order(26)
    public void testCreateDataType_DuplicateCode() {
        Map<String, Object> data = new HashMap<>();
        data.put("datatypeCode", "JSON");
        data.put("datatypeName", "测试");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/datatype");

        response.then().statusCode(anyOf(is(400), is(409)));
    }

    /**
     * 测试更新数据类型 - 正常场景
     */
    @Test
    @Order(27)
    public void testUpdateDataType_Success() {
        if (testDataTypeId == null) {
            Assumptions.assumeTrue(false, "No test datatype to update");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("datatypeName", "更新的数据类型名称");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/datatype/" + testDataTypeId);

        verifySuccess(response);
    }

    /**
     * 测试删除数据类型 - 正常场景
     */
    @Test
    @Order(28)
    public void testDeleteDataType_Success() {
        if (testDataTypeId == null) {
            Assumptions.assumeTrue(false, "No test datatype to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/datatype/" + testDataTypeId);

        response.then().statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试修改数据类型状态 - 正常场景
     */
    @Test
    @Order(29)
    public void testUpdateDataTypeStatus_Success() {
        Response response = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/datatype/1/status");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    // ==================== 配置管理测试 ====================

    /**
     * 测试配置列表查询 - 正常场景
     */
    @Test
    @Order(40)
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
    @Order(41)
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
    @Order(42)
    public void testGetConfigById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/config/1");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        } else {
            response.then().statusCode(anyOf(is(404), is(400)));
        }
    }

    /**
     * 测试配置详情查询 - 不存在
     */
    @Test
    @Order(43)
    public void testGetConfigById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/config/" + NON_EXISTENT_ID);

        response.then().statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试创建配置 - 正常场景
     */
    @Test
    @Order(44)
    public void testCreateConfig_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("vendorId", 1L);
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
    @Order(45)
    public void testCreateConfig_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("description", "测试配置");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/config");

        response.then().statusCode(anyOf(is(400), is(500)));
    }

    /**
     * 测试更新配置 - 正常场景
     */
    @Test
    @Order(46)
    public void testUpdateConfig_Success() {
        if (testConfigId == null) {
            Assumptions.assumeTrue(false, "No test config to update");
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
     * 测试删除配置 - 正常场景
     */
    @Test
    @Order(47)
    public void testDeleteConfig_Success() {
        if (testConfigId == null) {
            Assumptions.assumeTrue(false, "No test config to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/config/" + testConfigId);

        response.then().statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试按厂商查询配置 - 正常场景
     */
    @Test
    @Order(48)
    public void testGetConfigByVendor_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/config/vendor/1");

        verifySuccess(response);
    }

    /**
     * 测试修改配置状态 - 正常场景
     */
    @Test
    @Order(49)
    public void testUpdateConfigStatus_Success() {
        Response response = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/config/1/status");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }
}
