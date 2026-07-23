package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 调用方管理业务链路测试
 *
 * 模拟前端"调用方管理"页面的完整业务流程：
 * 1. 创建调用方 → 查询 → 修改 → 状态切换
 * 2. 创建 API Key → 状态切换
 * 3. 删除
 * 4. 边界测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CallerBusinessFlowTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(CallerBusinessFlowTest.class);

    private static Long testCallerId;
    private static Long testApiKeyId;

    // ==================== 链路1：创建调用方 ====================

    @Test
    @Order(1)
    @DisplayName("链路1-1: 查询调用方列表 → 验证接口可用")
    void testCallerList() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/caller/list");

        verifySuccess(response);
        log.info("调用方列表查询成功");
    }

    @Test
    @Order(2)
    @DisplayName("链路1-2: 创建调用方 → 提取ID")
    void testCreateCaller() {
        String code = uniqueId("CALLER");

        Map<String, Object> data = new HashMap<>();
        data.put("callerCode", code);
        data.put("callerName", "业务链路测试调用方");
        data.put("callerType", "app");
        data.put("description", "业务链路测试创建的调用方");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/caller");

        verifySuccess(response);
        testCallerId = extractId(response);
        Assertions.assertNotNull(testCallerId, "调用方创建成功后应返回ID");
        registerDeleteById("/caller/{id}", testCallerId);

        log.info("调用方创建成功, ID: {}, Code: {}", testCallerId, code);
    }

    @Test
    @Order(3)
    @DisplayName("链路1-3: 创建重复 callerCode → 验证409冲突")
    void testCreateCallerDuplicateCode() {
        org.junit.jupiter.api.Assertions.assertTrue(testCallerId != null, "需要测试调用方ID");

        // 获取已有调用方的 code
        Response detail = getAuthRequest()
            .when()
            .get("/caller/" + testCallerId);

        String existingCode = detail.jsonPath().getString("data.callerCode");
        org.junit.jupiter.api.Assertions.assertTrue(existingCode != null, "需要已有调用方代码");

        Map<String, Object> data = new HashMap<>();
        data.put("callerCode", existingCode);
        data.put("callerName", "重复代码测试调用方");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/caller");

        response.then().statusCode(409);
        log.info("重复callerCode冲突检测通过");
    }

    // ==================== 链路2：查询调用方 ====================

    @Test
    @Order(4)
    @DisplayName("链路2-1: 查询调用方详情 → 验证创建数据一致")
    void testCallerDetail() {
        org.junit.jupiter.api.Assertions.assertTrue(testCallerId != null, "需要测试调用方ID");

        Response response = getAuthRequest()
            .when()
            .get("/caller/" + testCallerId);

        verifySuccess(response);
        response.then()
            .body("data.id", notNullValue())
            .body("data.callerCode", notNullValue())
            .body("data.callerName", equalTo("业务链路测试调用方"));

        log.info("调用方详情验证通过");
    }

    @Test
    @Order(5)
    @DisplayName("链路2-2: 查询不存在的调用方 → 验证404")
    void testCallerNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/caller/" + NON_EXISTENT_ID);

        response.then().statusCode(404);
        log.info("不存在的调用方返回404验证通过");
    }

    // ==================== 链路3：更新调用方 ====================

    @Test
    @Order(6)
    @DisplayName("链路3-1: 更新调用方信息")
    void testUpdateCaller() {
        org.junit.jupiter.api.Assertions.assertTrue(testCallerId != null, "需要测试调用方ID");

        Map<String, Object> data = new HashMap<>();
        data.put("callerName", "更新后的业务链路测试调用方");
        data.put("description", "更新后的描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/caller/" + testCallerId);

        verifySuccess(response);

        // 验证更新生效
        Response detail = getAuthRequest()
            .when()
            .get("/caller/" + testCallerId);

        verifySuccess(detail);
        detail.then()
            .body("data.callerName", equalTo("更新后的业务链路测试调用方"));

        log.info("调用方更新验证通过");
    }

    // ==================== 链路4：调用方状态切换 ====================

    @Test
    @Order(8)
    @DisplayName("链路4-1: 切换调用方状态 inactive → active")
    void testCallerStatusToggle() {
        org.junit.jupiter.api.Assertions.assertTrue(testCallerId != null, "需要测试调用方ID");

        // 切换为 inactive
        Response inactiveResp = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/caller/" + testCallerId + "/status");

        inactiveResp.then().statusCode(anyOf(is(200), is(204)));

        // 切回 active
        Response activeResp = getAuthRequest()
            .body(Map.of("status", "active"))
            .when()
            .patch("/caller/" + testCallerId + "/status");

        activeResp.then().statusCode(anyOf(is(200), is(204)));

        log.info("调用方状态切换验证通过 (inactive → active)");
    }

    // ==================== 链路5：API Key 管理 ====================

    @Test
    @Order(10)
    @DisplayName("链路5-1: 创建 API Key → 提取ID")
    void testCreateApiKey() {
        org.junit.jupiter.api.Assertions.assertTrue(testCallerId != null, "需要测试调用方ID");

        Map<String, Object> data = new HashMap<>();
        data.put("callerId", testCallerId);
        data.put("name", uniqueId("测试API密钥"));

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/caller/apikey");

        verifySuccess(response);
        testApiKeyId = extractId(response);
        Assertions.assertNotNull(testApiKeyId, "API Key创建成功后应返回ID");
        registerDeleteById("/caller/apikey/{id}", testApiKeyId);

        log.info("API Key创建成功, ID: {}", testApiKeyId);
    }

    @Test
    @Order(11)
    @DisplayName("链路5-2: 创建 API Key 缺少 name → 验证400")
    void testCreateApiKeyMissingName() {
        org.junit.jupiter.api.Assertions.assertTrue(testCallerId != null, "需要测试调用方ID");

        Map<String, Object> data = new HashMap<>();
        data.put("callerId", testCallerId);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/caller/apikey");

        response.then().statusCode(400);
        log.info("缺少API Key name返回400验证通过");
    }

    // ==================== 链路6：API Key 状态切换 ====================

    @Test
    @Order(13)
    @DisplayName("链路6-1: 切换 API Key 状态 expired → active")
    void testApiKeyStatusToggle() {
        org.junit.jupiter.api.Assertions.assertTrue(testApiKeyId != null, "需要测试API Key ID");

        // 切换为 expired
        Response expiredResp = getAuthRequest()
            .body(Map.of("status", "expired"))
            .when()
            .put("/caller/apikey/" + testApiKeyId + "/status");

        expiredResp.then().statusCode(anyOf(is(200), is(204)));

        // 切换为 revoked
        Response revokedResp = getAuthRequest()
            .body(Map.of("status", "revoked"))
            .when()
            .put("/caller/apikey/" + testApiKeyId + "/status");

        revokedResp.then().statusCode(anyOf(is(200), is(204)));

        // 切回 active
        Response activeResp = getAuthRequest()
            .body(Map.of("status", "active"))
            .when()
            .put("/caller/apikey/" + testApiKeyId + "/status");

        activeResp.then().statusCode(anyOf(is(200), is(204)));

        log.info("API Key状态切换验证通过 (expired → revoked → active)");
    }

    // ==================== 链路7：删除 ====================

    @Test
    @Order(17)
    @DisplayName("链路7-1: 删除调用方 → 验证已删除")
    void testDeleteCaller() {
        org.junit.jupiter.api.Assertions.assertTrue(testCallerId != null, "需要测试调用方ID");

        Response response = getAuthRequest()
            .when()
            .delete("/caller/" + testCallerId);

        response.then().statusCode(anyOf(is(200), is(204)));

        // 验证已删除
        Response check = getAuthRequest()
            .when()
            .get("/caller/" + testCallerId);

        check.then().statusCode(anyOf(is(404), is(400)));

        testCallerId = null;
        testApiKeyId = null;
    }

    // ==================== 边界测试 ====================

    @Test
    @Order(60)
    @DisplayName("边界-1: 创建调用方缺少 callerName → 验证400")
    void testCreateCallerMissingName() {
        Map<String, Object> data = new HashMap<>();
        data.put("callerCode", uniqueId("CALLER_NO_NAME"));

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/caller");

        response.then().statusCode(400);
        log.info("缺少callerName返回400验证通过");
    }

    @Test
    @Order(61)
    @DisplayName("边界-2: 查询不存在的调用方 → 验证404")
    void testCallerNotFoundBoundary() {
        Response response = getAuthRequest()
            .when()
            .get("/caller/" + NON_EXISTENT_ID);

        response.then().statusCode(404);
        log.info("不存在的调用方返回404验证通过");
    }

    @Test
    @Order(62)
    @DisplayName("边界-3: 给不存在的调用方创建API Key → 验证404")
    void testCreateApiKeyCallerNotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("callerId", NON_EXISTENT_ID);
        data.put("name", "测试密钥");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/caller/apikey");

        response.then().statusCode(404);
        log.info("不存在的调用方创建API Key返回404验证通过");
    }

    @Test
    @Order(63)
    @DisplayName("边界-4: 未授权访问调用方列表 → 验证401")
    void testCallerListUnauthorized() {
        given()
            .when()
            .get("/caller/list")
            .then()
            .statusCode(401);

        log.info("未授权访问返回401验证通过");
    }

    @Test
    @Order(16)
    @DisplayName("边界-5: 修改API Key状态为无效值 → 验证400")
    void testApiKeyStatusInvalid() {
        // 需要一个存在的 API Key 来测试
        org.junit.jupiter.api.Assertions.assertTrue(testApiKeyId != null, "需要测试API Key ID");

        Response response = getAuthRequest()
            .body(Map.of("status", "invalid_status"))
            .when()
            .put("/caller/apikey/" + testApiKeyId + "/status");

        response.then().statusCode(400);
        log.info("无效API Key状态值返回400验证通过");
    }
}
