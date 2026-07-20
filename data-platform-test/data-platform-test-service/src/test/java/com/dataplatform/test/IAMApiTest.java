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
    private static String testRoleCode;
    private static Long testPermissionId;
    private static Long testCallerId;

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
    @Order(4)
    public void testGetUserById_Success() {
        skipIfNull(testUserId, "user");
        Response response = getAuthRequest()
            .when()
            .get("/user/" + testUserId);

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
    @Order(5)
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
    @Order(3)
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
        registerDeleteById("/user/{id}", testUserId);
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
        Long duplicateUserId = extractId(createResponse);
        Assertions.assertNotNull(duplicateUserId, "重复用户名测试的前置用户创建失败");
        registerDeleteById("/user/{id}", duplicateUserId);

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
    @Order(45)
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
        skipIfNull(testUserId, "user");
        Response response = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/user/" + testUserId + "/status");

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
        skipIfNull(testUserId, "user");
        Response response = getAuthRequest()
            .body(Map.of("password", "NewPass123"))
            .when()
            .post("/user/" + testUserId + "/reset-password");

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
        skipIfNull(testUserId, "user");
        Response response = getAuthRequest()
            .when()
            .get("/user/" + testUserId + "/roles");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试分配用户角色 - 正常场景
     */
    @Test
    @Order(34)
    public void testAssignUserRoles_Success() {
        skipIfNull(testUserId, "user");
        skipIfNull(testRoleId, "role");
        Response response = getAuthRequest()
            .body(Map.of("roleIds", new Long[]{testRoleId}))
            .when()
            .post("/user/" + testUserId + "/roles");

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
    @Order(23)
    public void testGetRoleById_Success() {
        skipIfNull(testRoleId, "role");
        Response response = getAuthRequest()
            .when()
            .get("/role/" + testRoleId);

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试角色详情查询 - 不存在
     */
    @Test
    @Order(24)
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
    @Order(22)
    public void testCreateRole_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("roleName", "测试角色_" + System.currentTimeMillis());
        testRoleCode = "TEST_ROLE_" + System.currentTimeMillis();
        data.put("roleCode", testRoleCode);
        data.put("description", "测试角色描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/role");

        verifySuccess(response);

        Integer id = response.jsonPath().getInt("data.id");
        Assertions.assertNotNull(id, "创建角色成功后必须返回角色 ID");
        testRoleId = id.longValue();
        registerDeleteById("/role/{id}", testRoleId);
    }

    /**
     * 测试创建角色 - 必填参数缺失
     */
    @Test
    @Order(26)
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
    @Order(27)
    public void testCreateRole_DuplicateCode() {
        skipIfNull(testRoleId, "role");
        Map<String, Object> data = new HashMap<>();
        data.put("roleName", "重复测试角色");
        data.put("roleCode", testRoleCode);

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
    @Order(28)
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
    @Order(46)
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
        skipIfNull(testRoleId, "role");
        Response response = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/role/" + testRoleId + "/status");

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
        skipIfNull(testRoleId, "role");
        Response response = getAuthRequest()
            .when()
            .get("/role/" + testRoleId + "/permissions");

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
        skipIfNull(testRoleId, "role");
        skipIfNull(testPermissionId, "permission");
        Response response = getAuthRequest()
            .body(Map.of("permissionIds", new Long[]{testPermissionId}))
            .when()
            .post("/role/" + testRoleId + "/permissions");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    // ==================== 用户调用方关联测试 ====================

    @Test
    @Order(34)
    public void testCreateCallerForUserAssignment() {
        String unique = String.valueOf(System.currentTimeMillis());
        Map<String, Object> data = new HashMap<>();
        data.put("callerName", "IAM关联测试调用方_" + unique);
        data.put("callerCode", "IAM_CALLER_" + unique);
        data.put("contactName", "IAM测试联系人");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/caller");

        verifySuccess(response);
        testCallerId = extractId(response);
        skipIfNull(testCallerId, "caller");
        registerDeleteById("/caller/{id}", testCallerId);
    }

    /**
     * 测试获取用户调用方列表 - 正常场景
     */
    @Test
    @Order(35)
    public void testGetUserCallers_Success() {
        skipIfNull(testUserId, "user");
        Response response = getAuthRequest()
            .when()
            .get("/user/" + testUserId + "/callers");

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试分配用户调用方 - 正常场景
     */
    @Test
    @Order(36)
    public void testAssignUserCallers_Success() {
        skipIfNull(testUserId, "user");
        skipIfNull(testCallerId, "caller");
        Response response = getAuthRequest()
            .body(Map.of("callerIds", new Long[]{testCallerId}))
            .when()
            .post("/user/" + testUserId + "/callers");

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
        skipIfNull(testPermissionId, "permission");
        Response response = getAuthRequest()
            .when()
            .get("/permission/" + testPermissionId);

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试创建权限 - 正常场景
     */
    @Test
    @Order(25)
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
        Integer id = response.jsonPath().getInt("data.id");
        Assertions.assertNotNull(id, "创建权限成功后必须返回权限 ID");
        testPermissionId = id.longValue();
        registerDeleteById("/permission/{id}", testPermissionId);
    }

    /**
     * 测试创建权限 - 必填参数缺失
     */
    @Test
    @Order(43)
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
    @Order(44)
    public void testUpdatePermission_Success() {
        skipIfNull(testPermissionId, "permission");
        Response response = getAuthRequest()
            .body(Map.of("description", "更新后的描述"))
            .when()
            .put("/permission/" + testPermissionId);

        verifySuccess(response);
        {
            verifySuccess(response);
        }
    }

    /**
     * 测试删除权限 - 正常场景
     */
    @Test
    @Order(47)
    public void testDeletePermission_Success() {
        skipIfNull(testPermissionId, "permission");
        Response deleteResponse = getAuthRequest()
            .when()
            .delete("/permission/" + testPermissionId);
        deleteResponse.then().statusCode(anyOf(is(200), is(204)));
    }
}
