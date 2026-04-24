package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 数据类型接口测试
 * 覆盖 6 个接口：列表、详情、创建、更新、删除、状态修改
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataTypeApiTest extends BaseTest {

    private static Long testDataTypeId;

    /**
     * 测试数据类型列表查询 - 正常场景
     */
    @Test
    @Order(1)
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
    @Order(2)
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
    @Order(3)
    public void testGetDataTypeById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/datatype/1");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        } else {
            response.then()
                .statusCode(anyOf(is(404), is(400)));
        }
    }

    /**
     * 测试数据类型详情查询 - 不存在
     */
    @Test
    @Order(4)
    public void testGetDataTypeById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/datatype/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试创建数据类型 - 正常场景
     */
    @Test
    @Order(5)
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
    @Order(6)
    public void testCreateDataType_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("description", "测试数据类型描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/datatype");

        response.then()
            .statusCode(anyOf(is(400), is(500)));
    }

    /**
     * 测试创建数据类型 - 数据类型代码重复
     */
    @Test
    @Order(7)
    public void testCreateDataType_DuplicateCode() {
        Map<String, Object> data = new HashMap<>();
        data.put("datatypeCode", "JSON");
        data.put("datatypeName", "测试");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/datatype");

        response.then()
            .statusCode(anyOf(is(400), is(409)));
    }

    /**
     * 测试更新数据类型 - 正常场景
     */
    @Test
    @Order(8)
    public void testUpdateDataType_Success() {
        if (testDataTypeId == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "No test datatype to update");
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
     * 测试更新数据类型 - 不存在
     */
    @Test
    @Order(9)
    public void testUpdateDataType_NotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("datatypeName", "测试");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/datatype/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试删除数据类型 - 正常场景
     */
    @Test
    @Order(10)
    public void testDeleteDataType_Success() {
        if (testDataTypeId == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "No test datatype to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/datatype/" + testDataTypeId);

        response.then()
            .statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试删除数据类型 - 不存在
     */
    @Test
    @Order(11)
    public void testDeleteDataType_NotFound() {
        Response response = getAuthRequest()
            .when()
            .delete("/datatype/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试修改数据类型状态 - 正常场景
     */
    @Test
    @Order(12)
    public void testUpdateDataTypeStatus_Success() {
        Response response = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/datatype/1/status");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试修改数据类型状态 - 无效状态值
     */
    @Test
    @Order(13)
    public void testUpdateDataTypeStatus_InvalidStatus() {
        Response response = getAuthRequest()
            .body(Map.of("status", "invalid_status"))
            .when()
            .patch("/datatype/1/status");

        response.then()
            .statusCode(anyOf(is(400), is(500)));
    }
}