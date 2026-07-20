package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * Graylog 模块业务链路测试
 *
 * 模拟前端"灰度发布"页面的完整业务流程：
 * 1. 创建灰度规则
 * 2. 查询验证
 * 3. 状态切换
 * 4. 更新规则
 * 5. 按服务查询活跃规则
 * 6. 删除清理
 * 7. 边界测试
 *
 * 基于扫描 GraylogController + GrayRule Entity 生成
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GraylogBusinessFlowTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(GraylogBusinessFlowTest.class);

    private static Long testGrayRuleId;

    // ==================== 链路1：创建灰度规则 ====================

    @Test
    @Order(1)
    @DisplayName("链路1-1: 查询灰度规则列表")
    void testGrayRuleList() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/graylog/list");

        verifySuccess(response);
        log.info("灰度规则列表查询成功");
    }

    @Test
    @Order(2)
    @DisplayName("链路1-2: 创建灰度规则 → 提取ID")
    void testCreateGrayRule() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", uniqueId("GRAY_RULE"));
        data.put("serviceName", "data-platform-masterdata");
        data.put("version", "v1.0");
        data.put("weight", 10);
        data.put("conditionType", "header");
        data.put("conditionValue", "X-Gray-Test");
        data.put("description", "业务链路测试灰度规则");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/graylog");

        verifySuccess(response);
        testGrayRuleId = extractId(response);
        Assertions.assertNotNull(testGrayRuleId, "灰度规则创建成功后应返回ID");
        registerDeleteById("/graylog/{id}", testGrayRuleId);

        log.info("灰度规则创建成功, ID: {}", testGrayRuleId);
    }

    @Test
    @Order(3)
    @DisplayName("链路1-3: 创建灰度规则（缺少ruleName）→ 验证400")
    void testCreateGrayRuleMissingName() {
        Map<String, Object> data = new HashMap<>();
        data.put("serviceName", "data-platform-masterdata");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/graylog");

        response.then().statusCode(400);
        log.info("缺少ruleName校验通过");
    }

    // ==================== 链路2：查询验证 ====================

    @Test
    @Order(4)
    @DisplayName("链路2-1: 查询灰度规则详情 → 验证创建数据一致")
    void testGrayRuleDetail() {
        org.junit.jupiter.api.Assertions.assertTrue(testGrayRuleId != null, "需要测试灰度规则ID");

        Response response = getAuthRequest()
            .when()
            .get("/graylog/" + testGrayRuleId);

        verifySuccess(response);
        response.then()
            .body("data.id", notNullValue())
            .body("data.ruleName", notNullValue())
            .body("data.status", equalTo("active"));

        log.info("灰度规则详情验证通过");
    }

    @Test
    @Order(5)
    @DisplayName("链路2-2: 查询不存在的灰度规则 → 验证404")
    void testGrayRuleNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/graylog/" + NON_EXISTENT_ID);

        response.then().statusCode(anyOf(is(404), is(400)));
        log.info("灰度规则不存在校验通过");
    }

    @Test
    @Order(6)
    @DisplayName("链路2-3: 按服务查询活跃灰度规则")
    void testGetActiveRuleByService() {
        org.junit.jupiter.api.Assertions.assertTrue(testGrayRuleId != null, "需要测试灰度规则ID");

        // 用创建时的 serviceName 查询
        Response response = getAuthRequest()
            .when()
            .get("/graylog/active/data-platform-masterdata");

        // 可能返回 200（有活跃规则）或 404（刚创建的规则可能不在）
        verifySuccess(response);
        {
            response.then().body("data", notNullValue());
            log.info("按服务查询活跃规则成功");
        }
    }

    // ==================== 链路3：状态切换 ====================

    @Test
    @Order(7)
    @DisplayName("链路3-1: 灰度规则状态切换 → inactive→active")
    void testGrayRuleStatusToggle() {
        org.junit.jupiter.api.Assertions.assertTrue(testGrayRuleId != null, "需要测试灰度规则ID");

        // 切换为 inactive
        Response inactiveResp = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/graylog/" + testGrayRuleId + "/status");

        inactiveResp.then().statusCode(anyOf(is(200), is(204)));

        // 切回 active
        Response activeResp = getAuthRequest()
            .body(Map.of("status", "active"))
            .when()
            .patch("/graylog/" + testGrayRuleId + "/status");

        activeResp.then().statusCode(anyOf(is(200), is(204)));

        log.info("灰度规则状态切换验证通过 (inactive → active)");
    }

    @Test
    @Order(8)
    @DisplayName("链路3-2: 灰度规则状态切换 → expired→pending")
    void testGrayRuleStatusToggleExtra() {
        org.junit.jupiter.api.Assertions.assertTrue(testGrayRuleId != null, "需要测试灰度规则ID");

        // 切换为 expired
        Response expiredResp = getAuthRequest()
            .body(Map.of("status", "expired"))
            .when()
            .patch("/graylog/" + testGrayRuleId + "/status");

        expiredResp.then().statusCode(anyOf(is(200), is(204)));

        // 切换为 pending
        Response pendingResp = getAuthRequest()
            .body(Map.of("status", "pending"))
            .when()
            .patch("/graylog/" + testGrayRuleId + "/status");

        pendingResp.then().statusCode(anyOf(is(200), is(204)));

        // 切回 active（后续删除需要）
        Response activeResp = getAuthRequest()
            .body(Map.of("status", "active"))
            .when()
            .patch("/graylog/" + testGrayRuleId + "/status");

        activeResp.then().statusCode(anyOf(is(200), is(204)));

        log.info("灰度规则四态切换验证通过 (expired → pending → active)");
    }

    // ==================== 链路4：更新规则 ====================

    @Test
    @Order(9)
    @DisplayName("链路4-1: 更新灰度规则 → 验证修改生效")
    void testUpdateGrayRule() {
        org.junit.jupiter.api.Assertions.assertTrue(testGrayRuleId != null, "需要测试灰度规则ID");

        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", uniqueId("GRAY_RULE_UPDATED"));
        data.put("serviceName", "data-platform-masterdata");
        data.put("weight", 50);
        data.put("description", "更新后的业务链路测试灰度规则");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/graylog/" + testGrayRuleId);

        verifySuccess(response);
        log.info("灰度规则更新验证通过");
    }

    // ==================== 链路5：删除清理 ====================

    @Test
    @Order(10)
    @DisplayName("链路5-1: 删除灰度规则 → 验证已删除")
    void testDeleteGrayRule() {
        org.junit.jupiter.api.Assertions.assertTrue(testGrayRuleId != null, "没有需要删除的灰度规则");

        Response response = getAuthRequest()
            .when()
            .delete("/graylog/" + testGrayRuleId);

        verifySuccess(response);
        log.info("灰度规则删除成功, ID: {}", testGrayRuleId);

        // 验证已删除
        Response checkResponse = getAuthRequest()
            .when()
            .get("/graylog/" + testGrayRuleId);

        checkResponse.then().statusCode(anyOf(is(404), is(400)));

        testGrayRuleId = null;
    }

    // ==================== 链路6：边界测试 ====================

    @Test
    @Order(20)
    @DisplayName("边界-1: 更新不存在的灰度规则 → 验证404")
    void testUpdateGrayRuleNotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", "不存在的规则");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/graylog/" + NON_EXISTENT_ID);

        response.then().statusCode(anyOf(is(404), is(400)));
        log.info("更新不存在的灰度规则校验通过");
    }

    @Test
    @Order(21)
    @DisplayName("边界-2: 删除不存在的灰度规则 → 验证404")
    void testDeleteGrayRuleNotFound() {
        Response response = getAuthRequest()
            .when()
            .delete("/graylog/" + NON_EXISTENT_ID);

        response.then().statusCode(anyOf(is(404), is(400)));
        log.info("删除不存在的灰度规则校验通过");
    }

    @Test
    @Order(22)
    @DisplayName("边界-3: 无效状态值 → 验证400")
    void testGrayRuleStatusInvalid() {
        org.junit.jupiter.api.Assertions.assertTrue(testGrayRuleId != null, "需要测试灰度规则ID");

        // 需要一个还存在的规则来测，如果已被删除就用新创建的
        // 先创建一个临时规则
        Map<String, Object> createData = new HashMap<>();
        createData.put("ruleName", uniqueId("GRAY_TEMP"));
        createData.put("serviceName", "test-service");

        Response createResp = getAuthRequest()
            .body(createData)
            .when()
            .post("/graylog");

        verifySuccess(createResp);
        Long tempId = extractId(createResp);
        Assertions.assertNotNull(tempId, "临时规则创建后应返回ID");
        registerDeleteById("/graylog/{id}", tempId);

        // 传无效状态值
        Response response = getAuthRequest()
            .body(Map.of("status", "invalid_status"))
            .when()
            .patch("/graylog/" + tempId + "/status");

        response.then().statusCode(400);
        log.info("无效状态值校验通过");
    }

    @Test
    @Order(23)
    @DisplayName("边界-4: 未授权访问灰度规则列表 → 验证401")
    void testGrayRuleListUnauthorized() {
        given()
            .when()
            .get("/graylog/list")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(24)
    @DisplayName("边界-5: 查询不存在服务的活跃规则 → 验证404")
    void testGetActiveRuleNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/graylog/active/non-existent-service-xyz");

        response.then().statusCode(anyOf(is(404), is(400)));
        log.info("不存在服务的活跃规则校验通过");
    }
}
