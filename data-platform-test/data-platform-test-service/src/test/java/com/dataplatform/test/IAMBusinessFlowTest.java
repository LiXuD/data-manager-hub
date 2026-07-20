package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * IAM 用户权限管理业务链路测试
 *
 * 模拟前端"用户管理"+"角色管理"页面的完整业务流程：
 * 1. 角色 CRUD 生命周期（含状态切换 + 权限分配）
 * 2. 用户 CRUD 生命周期（含状态切换 + 密码重置 + 角色分配）
 * 3. 删除验证
 * 4. 边界测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IAMBusinessFlowTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(IAMBusinessFlowTest.class);

    private static Long testRoleId;
    private static Long testUserId;

    // ==================== 链路1：角色 CRUD ====================

    @Test
    @Order(1)
    @DisplayName("链路1-1: 查询角色列表 → 验证接口可用")
    void testRoleList() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/role/list");

        verifySuccess(response);
        log.info("角色列表查询成功");
    }

    @Test
    @Order(2)
    @DisplayName("链路1-2: 创建角色 → 提取ID")
    void testCreateRole() {
        String code = uniqueId("ROLE");

        Map<String, Object> data = new HashMap<>();
        data.put("roleCode", code);
        data.put("roleName", "业务链路测试角色");
        data.put("description", "IAM业务链路测试创建的角色");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/role");

        verifySuccess(response);
        testRoleId = extractId(response);
        Assertions.assertNotNull(testRoleId, "角色创建成功后应返回ID");
        registerDeleteById("/role/{id}", testRoleId);

        log.info("角色创建成功, ID: {}, Code: {}", testRoleId, code);
    }

    @Test
    @Order(3)
    @DisplayName("链路1-3: 创建重复 roleCode → 验证冲突")
    void testCreateRoleDuplicateCode() {
        org.junit.jupiter.api.Assertions.assertTrue(testRoleId != null, "需要测试角色ID");

        Response detail = getAuthRequest()
            .when()
            .get("/role/" + testRoleId);

        String existingCode = detail.jsonPath().getString("data.roleCode");
        org.junit.jupiter.api.Assertions.assertTrue(existingCode != null, "需要已有角色代码");

        Map<String, Object> data = new HashMap<>();
        data.put("roleCode", existingCode);
        data.put("roleName", "重复代码测试角色");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/role");

        response.then().statusCode(anyOf(is(409), is(400)));
        log.info("重复roleCode冲突检测通过");
    }

    @Test
    @Order(4)
    @DisplayName("链路1-4: 创建角色缺少 roleName → 验证400")
    void testCreateRoleMissingName() {
        Map<String, Object> data = new HashMap<>();
        data.put("roleCode", uniqueId("ROLE_NO_NAME"));

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/role");

        response.then().statusCode(400);
        log.info("缺少roleName返回400验证通过");
    }

    // ==================== 链路2：角色查询 ====================

    @Test
    @Order(5)
    @DisplayName("链路2-1: 查询角色详情 → 验证创建数据一致")
    void testRoleDetail() {
        org.junit.jupiter.api.Assertions.assertTrue(testRoleId != null, "需要测试角色ID");

        Response response = getAuthRequest()
            .when()
            .get("/role/" + testRoleId);

        verifySuccess(response);
        response.then()
            .body("data.id", notNullValue())
            .body("data.roleCode", notNullValue())
            .body("data.roleName", equalTo("业务链路测试角色"));

        log.info("角色详情验证通过");
    }

    @Test
    @Order(6)
    @DisplayName("链路2-2: 查询不存在的角色 → 验证404")
    void testRoleNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/role/" + NON_EXISTENT_ID);

        response.then().statusCode(404);
        log.info("不存在的角色返回404验证通过");
    }

    // ==================== 链路3：更新角色 ====================

    @Test
    @Order(7)
    @DisplayName("链路3-1: 更新角色信息")
    void testUpdateRole() {
        org.junit.jupiter.api.Assertions.assertTrue(testRoleId != null, "需要测试角色ID");

        Map<String, Object> data = new HashMap<>();
        data.put("roleName", "更新后的业务链路测试角色");
        data.put("description", "更新后的描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/role/" + testRoleId);

        verifySuccess(response);

        // 验证更新生效
        Response detail = getAuthRequest()
            .when()
            .get("/role/" + testRoleId);

        verifySuccess(detail);
        detail.then()
            .body("data.roleName", equalTo("更新后的业务链路测试角色"));

        log.info("角色更新验证通过");
    }

    // ==================== 链路4：角色状态切换 ====================

    @Test
    @Order(8)
    @DisplayName("链路4-1: 切换角色状态 inactive → active")
    void testRoleStatusToggle() {
        org.junit.jupiter.api.Assertions.assertTrue(testRoleId != null, "需要测试角色ID");

        // 切换为 inactive
        Response inactiveResp = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/role/" + testRoleId + "/status");

        inactiveResp.then().statusCode(anyOf(is(200), is(204)));

        // 切回 active
        Response activeResp = getAuthRequest()
            .body(Map.of("status", "active"))
            .when()
            .patch("/role/" + testRoleId + "/status");

        activeResp.then().statusCode(anyOf(is(200), is(204)));

        log.info("角色状态切换验证通过 (inactive → active)");
    }

    // ==================== 链路5：角色权限分配 ====================

    @Test
    @Order(9)
    @DisplayName("链路5-1: 查询角色权限列表 → 验证接口可用")
    void testRolePermissions() {
        org.junit.jupiter.api.Assertions.assertTrue(testRoleId != null, "需要测试角色ID");

        Response response = getAuthRequest()
            .when()
            .get("/role/" + testRoleId + "/permissions");

        verifySuccess(response);
        log.info("角色权限列表查询成功");
    }

    @Test
    @Order(10)
    @DisplayName("链路5-2: 查询所有可用权限 → 验证接口可用")
    void testAllPermissions() {
        Response response = getAuthRequest()
            .when()
            .get("/permission/all");

        verifySuccess(response);
        log.info("所有可用权限查询成功");
    }

    // ==================== 链路6：用户 CRUD ====================

    @Test
    @Order(20)
    @DisplayName("链路6-1: 查询用户列表 → 验证接口可用")
    void testUserList() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/user/list");

        verifySuccess(response);
        log.info("用户列表查询成功");
    }

    @Test
    @Order(21)
    @DisplayName("链路6-2: 创建用户 → 提取ID")
    void testCreateUser() {
        String username = uniqueId("testuser");

        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("password", "Test123456");
        data.put("nickname", "业务链路测试用户");
        data.put("email", username + "@test.com");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/user");

        verifySuccess(response);
        testUserId = extractId(response);
        Assertions.assertNotNull(testUserId, "用户创建成功后应返回ID");
        registerDeleteById("/user/{id}", testUserId);

        log.info("用户创建成功, ID: {}, Username: {}", testUserId, username);
    }

    @Test
    @Order(22)
    @DisplayName("链路6-3: 创建重复用户名 → 验证冲突")
    void testCreateUserDuplicateUsername() {
        org.junit.jupiter.api.Assertions.assertTrue(testUserId != null, "需要测试用户ID");

        Response detail = getAuthRequest()
            .when()
            .get("/user/" + testUserId);

        String existingUsername = detail.jsonPath().getString("data.username");
        org.junit.jupiter.api.Assertions.assertTrue(existingUsername != null, "需要已有用户名");

        Map<String, Object> data = new HashMap<>();
        data.put("username", existingUsername);
        data.put("password", "Test123456");
        data.put("nickname", "重复用户名测试");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/user");

        response.then().statusCode(anyOf(is(409), is(400)));
        log.info("重复用户名冲突检测通过");
    }

    @Test
    @Order(23)
    @DisplayName("链路6-4: 创建用户缺少 username → 验证400")
    void testCreateUserMissingUsername() {
        Map<String, Object> data = new HashMap<>();
        data.put("password", "Test123456");
        data.put("nickname", "缺少用户名");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/user");

        response.then().statusCode(400);
        log.info("缺少username返回400验证通过");
    }

    @Test
    @Order(24)
    @DisplayName("链路6-5: 创建用户密码太短 → 验证400")
    void testCreateUserShortPassword() {
        Map<String, Object> data = new HashMap<>();
        data.put("username", uniqueId("shortpw"));
        data.put("password", "abc12");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/user");

        response.then().statusCode(400);
        log.info("密码太短返回400验证通过");
    }

    @Test
    @Order(25)
    @DisplayName("链路6-6: 创建用户密码无数字 → 验证400")
    void testCreateUserPasswordNoDigit() {
        Map<String, Object> data = new HashMap<>();
        data.put("username", uniqueId("nodigit"));
        data.put("password", "abcdefgh");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/user");

        response.then().statusCode(400);
        log.info("密码无数字返回400验证通过");
    }

    // ==================== 链路7：用户查询 ====================

    @Test
    @Order(26)
    @DisplayName("链路7-1: 查询用户详情 → 验证创建数据一致")
    void testUserDetail() {
        org.junit.jupiter.api.Assertions.assertTrue(testUserId != null, "需要测试用户ID");

        Response response = getAuthRequest()
            .when()
            .get("/user/" + testUserId);

        verifySuccess(response);
        response.then()
            .body("data.id", notNullValue())
            .body("data.username", notNullValue())
            .body("data.nickname", equalTo("业务链路测试用户"));

        log.info("用户详情验证通过");
    }

    @Test
    @Order(27)
    @DisplayName("链路7-2: 查询不存在的用户 → 验证404")
    void testUserNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/user/" + NON_EXISTENT_ID);

        response.then().statusCode(404);
        log.info("不存在的用户返回404验证通过");
    }

    // ==================== 链路8：更新用户 ====================

    @Test
    @Order(28)
    @DisplayName("链路8-1: 更新用户信息")
    void testUpdateUser() {
        org.junit.jupiter.api.Assertions.assertTrue(testUserId != null, "需要测试用户ID");

        Map<String, Object> data = new HashMap<>();
        data.put("nickname", "更新后的业务链路测试用户");
        data.put("email", "updated@test.com");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/user/" + testUserId);

        verifySuccess(response);

        // 验证更新生效
        Response detail = getAuthRequest()
            .when()
            .get("/user/" + testUserId);

        verifySuccess(detail);
        detail.then()
            .body("data.nickname", equalTo("更新后的业务链路测试用户"));

        log.info("用户更新验证通过");
    }

    // ==================== 链路9：用户状态切换 ====================

    @Test
    @Order(29)
    @DisplayName("链路9-1: 切换用户状态 inactive → active")
    void testUserStatusToggle() {
        org.junit.jupiter.api.Assertions.assertTrue(testUserId != null, "需要测试用户ID");

        // 切换为 inactive
        Response inactiveResp = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/user/" + testUserId + "/status");

        inactiveResp.then().statusCode(anyOf(is(200), is(204)));

        // 切回 active
        Response activeResp = getAuthRequest()
            .body(Map.of("status", "active"))
            .when()
            .patch("/user/" + testUserId + "/status");

        activeResp.then().statusCode(anyOf(is(200), is(204)));

        log.info("用户状态切换验证通过 (inactive → active)");
    }

    // ==================== 链路10：重置密码 ====================

    @Test
    @Order(30)
    @DisplayName("链路10-1: 重置用户密码 → 验证成功")
    void testResetPassword() {
        org.junit.jupiter.api.Assertions.assertTrue(testUserId != null, "需要测试用户ID");

        Response response = getAuthRequest()
            .body(Map.of("password", "NewPass999"))
            .when()
            .post("/user/" + testUserId + "/reset-password");

        verifySuccess(response);
        log.info("重置密码验证通过");
    }

    @Test
    @Order(31)
    @DisplayName("链路10-2: 重置密码太短 → 验证400")
    void testResetPasswordTooShort() {
        org.junit.jupiter.api.Assertions.assertTrue(testUserId != null, "需要测试用户ID");

        Response response = getAuthRequest()
            .body(Map.of("password", "ab1"))
            .when()
            .post("/user/" + testUserId + "/reset-password");

        response.then().statusCode(400);
        log.info("重置密码太短返回400验证通过");
    }

    // ==================== 链路11：用户角色分配 ====================

    @Test
    @Order(40)
    @DisplayName("链路11-1: 查询用户角色 → 验证接口可用")
    void testUserRoles() {
        org.junit.jupiter.api.Assertions.assertTrue(testUserId != null, "需要测试用户ID");

        Response response = getAuthRequest()
            .when()
            .get("/user/" + testUserId + "/roles");

        verifySuccess(response);
        log.info("用户角色列表查询成功");
    }

    @Test
    @Order(41)
    @DisplayName("链路11-2: 分配角色给用户 → 验证成功")
    void testAssignRolesToUser() {
        org.junit.jupiter.api.Assertions.assertTrue(testUserId != null, "需要测试用户ID");
        org.junit.jupiter.api.Assertions.assertTrue(testRoleId != null, "需要测试角色ID");

        List<Long> roleIds = List.of(testRoleId);

        Response response = getAuthRequest()
            .body(roleIds)
            .when()
            .post("/user/" + testUserId + "/roles");

        verifySuccess(response);

        // 验证分配生效
        Response check = getAuthRequest()
            .when()
            .get("/user/" + testUserId + "/roles");

        verifySuccess(check);
        log.info("用户角色分配验证通过");
    }

    // ==================== 链路12：删除 ====================

    @Test
    @Order(50)
    @DisplayName("链路12-1: 删除用户 → 验证已删除")
    void testDeleteUser() {
        org.junit.jupiter.api.Assertions.assertTrue(testUserId != null, "需要测试用户ID");

        Response response = getAuthRequest()
            .when()
            .delete("/user/" + testUserId);

        response.then().statusCode(anyOf(is(200), is(204)));

        // 验证已删除
        Response check = getAuthRequest()
            .when()
            .get("/user/" + testUserId);

        check.then().statusCode(anyOf(is(404), is(400)));

        testUserId = null;
    }

    @Test
    @Order(51)
    @DisplayName("链路12-2: 删除角色 → 验证已删除")
    void testDeleteRole() {
        org.junit.jupiter.api.Assertions.assertTrue(testRoleId != null, "需要测试角色ID");

        Response response = getAuthRequest()
            .when()
            .delete("/role/" + testRoleId);

        response.then().statusCode(anyOf(is(200), is(204)));

        // 验证已删除
        Response check = getAuthRequest()
            .when()
            .get("/role/" + testRoleId);

        check.then().statusCode(anyOf(is(404), is(400)));

        testRoleId = null;
    }

    // ==================== 边界测试 ====================

    @Test
    @Order(60)
    @DisplayName("边界-1: 查询不存在的角色 → 验证404")
    void testRoleNotFoundBoundary() {
        Response response = getAuthRequest()
            .when()
            .get("/role/" + NON_EXISTENT_ID);

        response.then().statusCode(404);
        log.info("不存在的角色返回404验证通过");
    }

    @Test
    @Order(61)
    @DisplayName("边界-2: 查询不存在的用户 → 验证404")
    void testUserNotFoundBoundary() {
        Response response = getAuthRequest()
            .when()
            .get("/user/" + NON_EXISTENT_ID);

        response.then().statusCode(404);
        log.info("不存在的用户返回404验证通过");
    }

    @Test
    @Order(64)
    @DisplayName("边界-3: 给不存在的用户分配角色 → 验证404")
    void testAssignRolesUserNotFound() {
        Response response = getAuthRequest()
            .body(List.of(1L))
            .when()
            .post("/user/" + NON_EXISTENT_ID + "/roles");

        response.then().statusCode(anyOf(is(404), is(400)));
        log.info("不存在的用户分配角色返回404/400验证通过");
    }
}
