package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 接口管理API测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class InterfaceApiTest extends BaseTest {

    private static Long testInterfaceId;

    @Test
    @Order(1)
    @DisplayName("获取接口列表 - 成功")
    public void testGetInterfaceList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/interface/list");

        verifySuccess(response);
    }

    @Test
    @Order(2)
    @DisplayName("获取接口列表 - 按数据类型筛选")
    public void testGetInterfaceList_FilterByDataType() {
        Response response = getAuthRequest()
            .queryParam("dataTypeId", 1)
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/interface/list");

        response.then().statusCode(200);
    }

    @Test
    @Order(3)
    @DisplayName("创建接口 - 成功")
    public void testCreateInterface_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("interfaceCode", "TEST_INTERFACE_" + System.currentTimeMillis());
        data.put("interfaceName", "测试接口");
        data.put("dataTypeId", 1);
        data.put("description", "测试接口描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/interface");

        verifySuccess(response);

        Integer id = response.jsonPath().getInt("data.id");
        if (id != null) {
            testInterfaceId = id.longValue();
        }
    }

    @Test
    @Order(4)
    @DisplayName("创建接口 - 缺少必填字段")
    public void testCreateInterface_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("description", "测试");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/interface");

        response.then().statusCode(400);
    }

    @Test
    @Order(5)
    @DisplayName("创建接口 - 编码重复")
    public void testCreateInterface_DuplicateCode() {
        Map<String, Object> data = new HashMap<>();
        data.put("interfaceCode", "PERSONAL_QUERY");
        data.put("interfaceName", "重复编码测试");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/interface");

        response.then().statusCode(anyOf(is(400), is(409)));
    }

    @Test
    @Order(6)
    @DisplayName("获取接口详情 - 成功")
    public void testGetInterfaceById_Success() {
        if (testInterfaceId == null) {
            org.junit.jupiter.api.Assertions.assertTrue(false, "No test interface created");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId);

        verifySuccess(response);
    }

    @Test
    @Order(7)
    @DisplayName("获取接口详情 - 不存在")
    public void testGetInterfaceById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/interface/999999999");

        response.then().statusCode(404);
    }

    @Test
    @Order(8)
    @DisplayName("更新接口 - 成功")
    public void testUpdateInterface_Success() {
        if (testInterfaceId == null) {
            org.junit.jupiter.api.Assertions.assertTrue(false, "No test interface to update");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("interfaceName", "更新后的接口名");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/interface/" + testInterfaceId);

        verifySuccess(response);
    }

    @Test
    @Order(9)
    @DisplayName("更新接口状态 - 成功")
    public void testUpdateInterfaceStatus_Success() {
        if (testInterfaceId == null) {
            org.junit.jupiter.api.Assertions.assertTrue(false, "No test interface to update");
            return;
        }

        Response response = getAuthRequest()
            .body(Map.of("status", "1"))
            .when()
            .patch("/interface/" + testInterfaceId + "/status");

        verifySuccess(response);
    }

    @Test
    @Order(10)
    @DisplayName("更新接口状态 - 无效状态")
    public void testUpdateInterfaceStatus_InvalidStatus() {
        Response response = getAuthRequest()
            .body(Map.of("status", "INVALID"))
            .when()
            .patch("/interface/1/status");

        response.then().statusCode(400);
    }

    @Test
    @Order(11)
    @DisplayName("获取接口Schema - 成功")
    public void testGetInterfaceSchema_Success() {
        if (testInterfaceId == null) {
            org.junit.jupiter.api.Assertions.assertTrue(false, "No test interface");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId + "/schema");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    @Test
    @Order(12)
    @DisplayName("更新接口Schema - 成功")
    public void testUpdateInterfaceSchema_Success() {
        if (testInterfaceId == null) {
            org.junit.jupiter.api.Assertions.assertTrue(false, "No test interface");
            return;
        }

        Map<String, String> schema = new HashMap<>();
        schema.put("requestSchema", "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
        schema.put("responseSchema", "{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"integer\"}}}");

        Response response = getAuthRequest()
            .body(schema)
            .when()
            .put("/interface/" + testInterfaceId + "/schema");

        verifySuccess(response);
    }

    @Test
    @Order(13)
    @DisplayName("验证Schema格式 - 有效")
    public void testValidateSchema_Valid() {
        Map<String, String> data = new HashMap<>();
        data.put("schema", "{\"type\":\"object\"}");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/interface/schema/validate");

        verifySuccess(response);
        response.then().body("data.valid", equalTo(true));
    }

    @Test
    @Order(14)
    @DisplayName("验证Schema格式 - 无效")
    public void testValidateSchema_Invalid() {
        Map<String, String> data = new HashMap<>();
        data.put("schema", "not a valid json");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/interface/schema/validate");

        verifySuccess(response);
        response.then().body("data.valid", equalTo(false));
    }

    @Test
    @Order(15)
    @DisplayName("获取接口调用统计 - 成功")
    public void testGetInterfaceStats_Success() {
        if (testInterfaceId == null) {
            org.junit.jupiter.api.Assertions.assertTrue(false, "No test interface");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId + "/stats");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    @Test
    @Order(16)
    @DisplayName("获取接口日统计 - 成功")
    public void testGetInterfaceDailyStats_Success() {
        if (testInterfaceId == null) {
            org.junit.jupiter.api.Assertions.assertTrue(false, "No test interface");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId + "/stats/daily");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    @Test
    @Order(17)
    @DisplayName("按数据类型获取接口列表 - 成功")
    public void testListByDataType_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/interface/by-data-type/1");

        response.then().statusCode(200);
    }

    @Test
    @Order(99)
    @DisplayName("删除接口 - 成功")
    public void testDeleteInterface_Success() {
        if (testInterfaceId == null) {
            org.junit.jupiter.api.Assertions.assertTrue(false, "No test interface to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/interface/" + testInterfaceId);

        response.then().statusCode(anyOf(is(200), is(204)));
    }

    @Test
    @Order(100)
    @DisplayName("删除接口 - 不存在")
    public void testDeleteInterface_NotFound() {
        Response response = getAuthRequest()
            .when()
            .delete("/interface/999999999");

        response.then().statusCode(404);
    }
}
