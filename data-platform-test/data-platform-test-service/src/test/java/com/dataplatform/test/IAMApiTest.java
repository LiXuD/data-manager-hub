package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * IAM 用户权限管理接口测试
 *
 * 整合原 UserApiTest + RoleApiTest，对应 data-platform-identity 服务
 *
 * 覆盖接口：
 * - 用户管理：列表、详情、创建、更新、删除、状态修改、密码重置、角色获取、角色分配
 * - 角色管理：列表、详情、创建、更新、删除、状态修改、权限获取、权限分配
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IAMApiTest extends BaseTest {

    private static Long testUserId;
    private static Long testRoleId;

    // ==================== 用户管理测试 ====================

    /**
     * 测试用户列表查询 - 正常场景
     */
    @Test
    @Order(1)
    public void testGetUserList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/user/list");

        verifySuccess(response);
        response.then()
            .body("data", notNullValue())
            .body("data.list", notNullValue());
    }

    /**
     * 测试用户列表查询 - 未授权
     */
    @Test
    @Order(2)
    public void testGetUserList_Unauthorized() {
        given()
            .contentType("application/json")
            .when()
            .get("/user/list")
            .then()
            .statusCode(401);
    }

    /**
     * 测试用户详情查询 - 正常场景
     */
    @Test
    @Order(3)
    public void testGetUserById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/user/1");

        verifySuccess(response);
        {
            verifySuccess(response);
            response.then().body("data", notNullValue());
        }
    }

    /**
     * 测试用户详情查询 - 不存在
     */
    @Test
    @Order(4)
    public void testGetUserById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/user/999999999");

        response.then().statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试创建用户 - 正常场景
     */
    @Test
    @Order(5)
    public void testCreateUser_Success() {
        String username = "testuser_" + System.currentTimeMillis();
        Map<String, Object> userData = new HashMap<>();
        userData.put("username", username);
        userData.put("password", "Test123456");
        userData.put("realName", "测试用户");
        userData.put("email", "test@example.com");
        userData.put("phone", "13800138000");

        Response response = getAuthRequest()
            .body(userData)
            .when()
            .post("/user");

        verifySuccess(response);

        Integer id = response.jsonPath().getInt("data.id");
        Assertions.assertNotNull(id, "创建用户成功后必须返回用户 ID");
        testUserId = id.longValue();
    }

    /**
     * 测试创建用户 - 用户名重复
     */
    @Test
    @Order(6)
    public void testCreateUser_DuplicateUsername() {
        String username = "duplicate_user_" + System.currentTimeMillis();
        Map<String, Object> userData = new HashMap<>();
        userData.put("username", username);
        userData.put("password", "Test123456");

        Response createResponse = getAuthRequest()
            .body(userData)
            .when()
            .post("/user");
        verifySuccess(createResponse);

        Response response = getAuthRequest()
            .body(userData)
            .when()
            .post("/user");

        response.then().statusCode(anyOf(is(400), is(409)));
    }

    /**
     * 测试创建用户 - 必填参数缺失
     */
    @Test
    @Order(7)
    public void testCreateUser_MissingRequired() {
        Map<String, Object> userData = new HashMap<>();
        userData.put("realName", "测试用户");

        Response response = getAuthRequest()
            .body(userData)
            .when()
            .post("/user");

        response.then().statusCode(400);
    }

    /**
     * 测试更新用户 - 正常场景
     */
    @Test
    @Order(8)
    public void testUpdateUser_Success() {
        if (testUserId == null) {
            org.junit.jupiter.api.Assertions.assertTrue(false, "No test user to update");
            return;
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("realName", "更新后的名称");

        Response response = getAuthRequest()
            .body(userData)
            .when()
            .put("/user/" + testUserId);

        verifySuccess(response);
    }

    /**
     * 测试删除用户 - 正常场景
     */
    @Test
    @Order(9)
    public void testDeleteUser_Success() {
        if (testUserId == null) {
            org.junit.jupiter.api.Assertions.assertTrue(false, "No test user to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/user/" + testUserId);

        response.then().statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试修改用户状态 - 正常场景
     */
    @Test
    @Order(10)
    public void testUpdateUserStatus_Success() {
        Response response = getAuthRequest()
            .body(Map.of("status", "1"))
            .when()
            .patch("/user/1/status");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试重置密码 - 正常场景
     */
    @Test
    @Order(11)
    public void testResetPassword_Success() {
        Response response = getAuthRequest()
            .body(Map.of("password", "NewPass123"))
            .when()
            .post("/user/1/reset-password");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试获取用户角色 - 正常场景
     */
    @Test
    @Order(12)
    public void testGetUserRoles_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/user/1/roles");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试分配用户角色 - 正常场景
     */
    @Test
    @Order(13)
    public void testAssignUserRoles_Success() {
        Response response = getAuthRequest()
            .body(Map.of("roleIds", new Integer[]{1}))
            .when()
            .post("/user/1/roles");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    // ==================== 角色管理测试 ====================

    /**
     * 测试角色列表查询 - 正常场景
     */
    @Test
    @Order(20)
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
    @Order(21)
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
    @Order(22)
    public void testGetRoleById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/role/1");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试角色详情查询 - 不存在
     */
    @Test
    @Order(23)
    public void testGetRoleById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/role/999999999");

        response.then().statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试创建角色 - 正常场景
     */
    @Test
    @Order(24)
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
    @Order(25)
    public void testCreateRole_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("description", "测试角色描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/role");

        response.then().statusCode(400);
    }

    /**
     * 测试创建角色 - 角色代码重复
     */
    @Test
    @Order(26)
    public void testCreateRole_DuplicateCode() {
        Map<String, Object> data = new HashMap<>();
        data.put("roleName", "管理员");
        data.put("roleCode", "ADMIN");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/role");

        response.then().statusCode(anyOf(is(400), is(409)));
    }

    /**
     * 测试更新角色 - 正常场景
     */
    @Test
    @Order(27)
    public void testUpdateRole_Success() {
        if (testRoleId == null) {
            org.junit.jupiter.api.Assertions.assertTrue(false, "No test role to update");
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
     * 测试删除角色 - 正常场景
     */
    @Test
    @Order(28)
    public void testDeleteRole_Success() {
        if (testRoleId == null) {
            org.junit.jupiter.api.Assertions.assertTrue(false, "No test role to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/role/" + testRoleId);

        response.then().statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试修改角色状态 - 正常场景
     */
    @Test
    @Order(29)
    public void testUpdateRoleStatus_Success() {
        Response response = getAuthRequest()
            .body(Map.of("status", "1"))
            .when()
            .patch("/role/1/status");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试获取角色权限 - 正常场景
     */
    @Test
    @Order(30)
    public void testGetRolePermissions_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/role/1/permissions");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试分配角色权限 - 正常场景
     */
    @Test
    @Order(31)
    public void testAssignRolePermissions_Success() {
        Response response = getAuthRequest()
            .body(Map.of("permissionIds", new Integer[]{1, 2, 3}))
            .when()
            .post("/role/1/permissions");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    // ==================== 用户调用方关联测试 ====================

    /**
     * 测试获取用户调用方列表 - 正常场景
     */
    @Test
    @Order(32)
    public void testGetUserCallers_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/user/1/callers");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试分配用户调用方 - 正常场景
     */
    @Test
    @Order(33)
    public void testAssignUserCallers_Success() {
        Response response = getAuthRequest()
            .body(Map.of("callerIds", new Integer[]{1}))
            .when()
            .post("/user/1/callers");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    // ==================== 权限管理测试 ====================

    /**
     * 测试权限列表查询 - 正常场景
     */
    @Test
    @Order(40)
    public void testGetPermissionList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/permission/list");

        verifySuccess(response);
    }

    /**
     * 测试权限列表查询 - 未授权
     */
    @Test
    @Order(41)
    public void testGetPermissionList_Unauthorized() {
        given()
            .when()
            .get("/permission/list")
            .then()
            .statusCode(401);
    }

    /**
     * 测试权限详情查询 - 正常场景
     */
    @Test
    @Order(42)
    public void testGetPermissionById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/permission/1");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试创建权限 - 正常场景
     */
    @Test
    @Order(43)
    public void testCreatePermission_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("permissionCode", "TEST_PERM_" + System.currentTimeMillis());
        data.put("permissionName", "测试权限");
        data.put("resource", "test");
        data.put("action", "view");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/permission");

        verifySuccess(response);
    }

    /**
     * 测试创建权限 - 必填参数缺失
     */
    @Test
    @Order(44)
    public void testCreatePermission_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("permissionName", "测试权限");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/permission");

        response.then().statusCode(400);
    }

    /**
     * 测试更新权限 - 正常场景
     */
    @Test
    @Order(45)
    public void testUpdatePermission_Success() {
        Response response = getAuthRequest()
            .body(Map.of("description", "更新后的描述"))
            .when()
            .put("/permission/1");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试删除权限 - 正常场景
     */
    @Test
    @Order(46)
    public void testDeletePermission_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("permissionCode", "TEMP_DELETE_" + System.currentTimeMillis());
        data.put("permissionName", "临时权限");
        data.put("resource", "temp");
        data.put("action", "test");

        Response createResponse = getAuthRequest()
            .body(data)
            .when()
            .post("/permission");

        verifySuccess(createResponse);
        Integer id = createResponse.jsonPath().getInt("data.id");
        org.junit.jupiter.api.Assertions.assertNotNull(id, "创建权限后应返回ID");
        Response deleteResponse = getAuthRequest()
            .when()
            .delete("/permission/" + id);
        deleteResponse.then().statusCode(anyOf(is(200), is(204)));
    }
}
