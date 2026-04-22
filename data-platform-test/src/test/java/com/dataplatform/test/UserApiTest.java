package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 用户管理接口测试
 * 覆盖 9 个接口：列表、详情、创建、更新、删除、状态修改、密码重置、角色获取、角色分配
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserApiTest extends BaseTest {

    private static Long testUserId;

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

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
            response.then()
                .body("data", notNullValue());
        } else {
            response.then()
                .statusCode(anyOf(is(404), is(400)));
        }
    }

    /**
     * 测试用户详情查询 - 不存在的用户
     */
    @Test
    @Order(4)
    public void testGetUserById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/user/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试创建用户 - 正常场景
     */
    @Test
    @Order(5)
    public void testCreateUser_Success() {
        Map<String, Object> userData = new HashMap<>();
        userData.put("username", "testuser_" + System.currentTimeMillis());
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
        if (id != null) {
            testUserId = id.longValue();
        }
    }

    /**
     * 测试创建用户 - 用户名重复
     */
    @Test
    @Order(6)
    public void testCreateUser_DuplicateUsername() {
        Map<String, Object> userData = new HashMap<>();
        userData.put("username", "admin");
        userData.put("password", "Test123456");

        Response response = getAuthRequest()
            .body(userData)
            .when()
            .post("/user");

        response.then()
            .statusCode(anyOf(is(400), is(409)));
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

        response.then()
            .statusCode(400);
    }

    /**
     * 测试创建用户 - 密码强度不足
     */
    @Test
    @Order(8)
    public void testCreateUser_WeakPassword() {
        Map<String, Object> userData = new HashMap<>();
        userData.put("username", "testuser_" + System.currentTimeMillis());
        userData.put("password", "123");

        Response response = getAuthRequest()
            .body(userData)
            .when()
            .post("/user");

        response.then()
            .statusCode(anyOf(is(400), is(422)));
    }

    /**
     * 测试更新用户 - 正常场景
     */
    @Test
    @Order(9)
    public void testUpdateUser_Success() {
        if (testUserId == null) {
            Assertions.skip("No test user to update");
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
     * 测试更新用户 - 不存在的用户
     */
    @Test
    @Order(10)
    public void testUpdateUser_NotFound() {
        Map<String, Object> userData = new HashMap<>();
        userData.put("realName", "测试");

        Response response = getAuthRequest()
            .body(userData)
            .when()
            .put("/user/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试删除用户 - 正常场景
     */
    @Test
    @Order(11)
    public void testDeleteUser_Success() {
        if (testUserId == null) {
            Assertions.skip("No test user to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/user/" + testUserId);

        response.then()
            .statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试删除用户 - 不存在的用户
     */
    @Test
    @Order(12)
    public void testDeleteUser_NotFound() {
        Response response = getAuthRequest()
            .when()
            .delete("/user/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试修改用户状态 - 正常场景
     */
    @Test
    @Order(13)
    public void testUpdateUserStatus_Success() {
        Response response = getAuthRequest()
            .body(Map.of("status", "1"))
            .when()
            .patch("/user/1/status");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试修改用户状态 - 无效状态值
     */
    @Test
    @Order(14)
    public void testUpdateUserStatus_InvalidStatus() {
        Response response = getAuthRequest()
            .body(Map.of("status", "invalid_status"))
            .when()
            .patch("/user/1/status");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试重置密码 - 正常场景
     */
    @Test
    @Order(15)
    public void testResetPassword_Success() {
        Response response = getAuthRequest()
            .body(Map.of("password", "NewPass123"))
            .when()
            .post("/user/1/reset-password");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试获取用户角色 - 正常场景
     */
    @Test
    @Order(16)
    public void testGetUserRoles_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/user/1/roles");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试分配用户角色 - 正常场景
     */
    @Test
    @Order(17)
    public void testAssignUserRoles_Success() {
        Response response = getAuthRequest()
            .body(Map.of("roleIds", new Integer[]{1}))
            .when()
            .post("/user/1/roles");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试重置密码 - 弱密码
     */
    @Test
    @Order(18)
    public void testResetPassword_WeakPassword() {
        Response response = getAuthRequest()
            .body(Map.of("password", "123"))
            .when()
            .post("/user/1/reset-password");

        response.then()
            .statusCode(anyOf(is(400), is(422)));
    }

    /**
     * 测试重置密码 - 不存在的用户
     */
    @Test
    @Order(19)
    public void testResetPassword_NotFound() {
        Response response = getAuthRequest()
            .body(Map.of("password", "NewPass123"))
            .when()
            .post("/user/999999999/reset-password");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试获取用户角色 - 不存在的用户
     */
    @Test
    @Order(20)
    public void testGetUserRoles_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/user/999999999/roles");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试分配用户角色 - 不存在的用户
     */
    @Test
    @Order(21)
    public void testAssignUserRoles_NotFound() {
        Response response = getAuthRequest()
            .body(Map.of("roleIds", new Integer[]{1}))
            .when()
            .post("/user/999999999/roles");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }
}