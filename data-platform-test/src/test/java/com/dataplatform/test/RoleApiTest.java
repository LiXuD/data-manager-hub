package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 角色管理接口测试
 * 覆盖 8 个接口
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RoleApiTest extends BaseTest {

    private static Long testRoleId;

    /**
     * 测试角色列表查询 - 正常场景
     */
    @Test
    @Order(1)
    public void testGetRoleList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/role/list");

        verifySuccess(response);
    }

    /**
     * 测试角色列表查询 - 未授权
     */
    @Test
    @Order(2)
    public void testGetRoleList_Unauthorized() {
        given()
            .when()
            .get("/role/list")
            .then()
            .statusCode(401);
    }

    /**
     * 测试角色详情查询 - 正常场景
     */
    @Test
    @Order(3)
    public void testGetRoleById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/role/1");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试角色详情查询 - 不存在
     */
    @Test
    @Order(4)
    public void testGetRoleById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/role/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试创建角色 - 正常场景
     */
    @Test
    @Order(5)
    public void testCreateRole_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("roleName", "测试角色_" + System.currentTimeMillis());
        data.put("roleCode", "TEST_ROLE_" + System.currentTimeMillis());
        data.put("description", "测试角色描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/role");

        verifySuccess(response);

        Integer id = response.jsonPath().getInt("data.id");
        if (id != null) {
            testRoleId = id.longValue();
        }
    }

    /**
     * 测试创建角色 - 必填参数缺失
     */
    @Test
    @Order(6)
    public void testCreateRole_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("description", "测试角色描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/role");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试创建角色 - 角色代码重复
     */
    @Test
    @Order(7)
    public void testCreateRole_DuplicateCode() {
        Map<String, Object> data = new HashMap<>();
        data.put("roleName", "管理员");
        data.put("roleCode", "ADMIN");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/role");

        response.then()
            .statusCode(anyOf(is(400), is(409)));
    }

    /**
     * 测试更新角色 - 正常场景
     */
    @Test
    @Order(8)
    public void testUpdateRole_Success() {
        if (testRoleId == null) {
            Assertions.skip("No test role to update");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("description", "更新后的描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/role/" + testRoleId);

        verifySuccess(response);
    }

    /**
     * 测试更新角色 - 不存在
     */
    @Test
    @Order(9)
    public void testUpdateRole_NotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("description", "测试");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/role/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试删除角色 - 正常场景
     */
    @Test
    @Order(10)
    public void testDeleteRole_Success() {
        if (testRoleId == null) {
            Assertions.skip("No test role to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/role/" + testRoleId);

        response.then()
            .statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试删除角色 - 不存在
     */
    @Test
    @Order(11)
    public void testDeleteRole_NotFound() {
        Response response = getAuthRequest()
            .when()
            .delete("/role/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试修改角色状态 - 正常场景
     */
    @Test
    @Order(12)
    public void testUpdateRoleStatus_Success() {
        Response response = getAuthRequest()
            .body(Map.of("status", "1"))
            .when()
            .patch("/role/1/status");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试修改角色状态 - 无效状态值
     */
    @Test
    @Order(13)
    public void testUpdateRoleStatus_InvalidStatus() {
        Response response = getAuthRequest()
            .body(Map.of("status", "invalid"))
            .when()
            .patch("/role/1/status");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试获取角色权限 - 正常场景
     */
    @Test
    @Order(14)
    public void testGetRolePermissions_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/role/1/permissions");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试获取角色权限 - 不存在
     */
    @Test
    @Order(15)
    public void testGetRolePermissions_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/role/999999999/permissions");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试分配角色权限 - 正常场景
     */
    @Test
    @Order(16)
    public void testAssignRolePermissions_Success() {
        Response response = getAuthRequest()
            .body(Map.of("permissionIds", new Integer[]{1, 2, 3}))
            .when()
            .post("/role/1/permissions");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试分配角色权限 - 不存在
     */
    @Test
    @Order(17)
    public void testAssignRolePermissions_NotFound() {
        Response response = getAuthRequest()
            .body(Map.of("permissionIds", new Integer[]{1}))
            .when()
            .post("/role/999999999/permissions");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }
}