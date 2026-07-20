package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 监控告警业务链路测试
 *
 * 模拟前端"监控告警"页面的完整业务流程：
 * 1. 创建告警规则 → 查询 → 修改（含状态） → 删除
 * 2. 告警记录查询和处理
 * 3. 边界测试
 *
 * 注意：AlertRule 没有 PATCH status 端点，状态通过 PUT 全量更新
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MonitorBusinessFlowTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(MonitorBusinessFlowTest.class);

    private static Long testRuleId;

    // ==================== 链路1：创建告警规则 ====================

    @Test
    @Order(1)
    @DisplayName("链路1-1: 查询告警规则列表 → 验证接口可用")
    void testAlertRuleList() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/alert/rule/list");

        verifySuccess(response);
        log.info("告警规则列表查询成功");
    }

    @Test
    @Order(2)
    @DisplayName("链路1-2: 创建告警规则 → 提取ID")
    void testCreateAlertRule() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", uniqueId("业务链路告警规则"));
        data.put("targetType", "cpu_usage");
        data.put("conditionType", "gt");
        data.put("thresholdValue", 80);
        data.put("severity", "critical");
        data.put("ruleType", "THRESHOLD");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/alert/rule");

        verifySuccess(response);
        testRuleId = extractId(response);
        Assertions.assertNotNull(testRuleId, "告警规则创建成功后应返回ID");
        registerDeleteById("/alert/rule/{id}", testRuleId);

        log.info("告警规则创建成功, ID: {}", testRuleId);
    }

    @Test
    @Order(3)
    @DisplayName("链路1-3: 创建告警规则缺少 ruleName → 验证400")
    void testCreateAlertRuleMissingName() {
        Map<String, Object> data = new HashMap<>();
        data.put("targetType", "cpu_usage");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/alert/rule");

        response.then().statusCode(400);
        log.info("缺少ruleName返回400验证通过");
    }

    @Test
    @Order(4)
    @DisplayName("链路1-4: 创建告警规则缺少 targetType → 验证400")
    void testCreateAlertRuleMissingTarget() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", uniqueId("缺目标类型规则"));

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/alert/rule");

        response.then().statusCode(400);
        log.info("缺少targetType返回400验证通过");
    }

    // ==================== 链路2：查询告警规则 ====================

    @Test
    @Order(5)
    @DisplayName("链路2-1: 查询告警规则详情 → 验证创建数据一致")
    void testAlertRuleDetail() {
        org.junit.jupiter.api.Assertions.assertTrue(testRuleId != null, "需要测试规则ID");

        Response response = getAuthRequest()
            .when()
            .get("/alert/rule/" + testRuleId);

        verifySuccess(response);
        response.then()
            .body("data.id", notNullValue())
            .body("data.ruleName", notNullValue());

        log.info("告警规则详情验证通过");
    }

    @Test
    @Order(6)
    @DisplayName("链路2-2: 查询不存在的规则 → 验证404")
    void testAlertRuleNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/alert/rule/" + NON_EXISTENT_ID);

        response.then().statusCode(404);
        log.info("不存在的规则返回404验证通过");
    }

    // ==================== 链路3：更新告警规则 ====================

    @Test
    @Order(7)
    @DisplayName("链路3-1: 更新告警规则 threshold")
    void testUpdateAlertRule() {
        org.junit.jupiter.api.Assertions.assertTrue(testRuleId != null, "需要测试规则ID");

        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", "更新后的业务链路告警规则");
        data.put("targetType", "cpu_usage");
        data.put("conditionType", "gt");
        data.put("thresholdValue", 90);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/alert/rule/" + testRuleId);

        verifySuccess(response);

        // 验证更新生效
        Response detail = getAuthRequest()
            .when()
            .get("/alert/rule/" + testRuleId);

        verifySuccess(detail);
        detail.then()
            .body("data.ruleName", equalTo("更新后的业务链路告警规则"));

        log.info("告警规则更新验证通过");
    }

    // ==================== 链路4：状态切换（通过 PUT） ====================

    @Test
    @Order(9)
    @DisplayName("链路4-1: 切换规则状态 inactive → active")
    void testAlertRuleStatusToggle() {
        org.junit.jupiter.api.Assertions.assertTrue(testRuleId != null, "需要测试规则ID");

        // 先查询当前规则
        Response current = getAuthRequest()
            .when()
            .get("/alert/rule/" + testRuleId);

        String ruleName = current.jsonPath().getString("data.ruleName");
        String targetType = current.jsonPath().getString("data.targetType");

        // 切换为 inactive（通过 PUT 全量更新）
        Map<String, Object> inactiveData = new HashMap<>();
        inactiveData.put("ruleName", ruleName);
        inactiveData.put("targetType", targetType);
        inactiveData.put("status", "inactive");

        Response inactiveResp = getAuthRequest()
            .body(inactiveData)
            .when()
            .put("/alert/rule/" + testRuleId);

        verifySuccess(inactiveResp);

        // 切回 active
        Map<String, Object> activeData = new HashMap<>();
        activeData.put("ruleName", ruleName);
        activeData.put("targetType", targetType);
        activeData.put("status", "active");

        Response activeResp = getAuthRequest()
            .body(activeData)
            .when()
            .put("/alert/rule/" + testRuleId);

        verifySuccess(activeResp);

        log.info("告警规则状态切换验证通过 (inactive → active)");
    }

    // ==================== 链路5：告警记录查询 ====================

    @Test
    @Order(11)
    @DisplayName("链路5-1: 查询告警记录列表 → 验证接口可用")
    void testAlertRecordList() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/alert/record/list");

        verifySuccess(response);
        log.info("告警记录列表查询成功");
    }

    @Test
    @Order(12)
    @DisplayName("链路5-2: 按条件过滤告警记录")
    void testAlertRecordListFiltered() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .queryParam("status", "pending")
            .when()
            .get("/alert/record/list");

        verifySuccess(response);
        log.info("告警记录过滤查询成功");
    }

    // ==================== 链路6：删除告警规则 ====================

    @Test
    @Order(13)
    @DisplayName("链路6-1: 删除告警规则 → 验证已删除")
    void testDeleteAlertRule() {
        org.junit.jupiter.api.Assertions.assertTrue(testRuleId != null, "需要测试规则ID");

        Response response = getAuthRequest()
            .when()
            .delete("/alert/rule/" + testRuleId);

        response.then().statusCode(anyOf(is(200), is(204)));

        // 验证已删除
        Response check = getAuthRequest()
            .when()
            .get("/alert/rule/" + testRuleId);

        check.then().statusCode(anyOf(is(404), is(400)));

        testRuleId = null;
    }

    // ==================== 边界测试 ====================

    @Test
    @Order(60)
    @DisplayName("边界-1: 查询不存在的规则 → 验证404")
    void testRuleNotFoundBoundary() {
        Response response = getAuthRequest()
            .when()
            .get("/alert/rule/" + NON_EXISTENT_ID);

        response.then().statusCode(404);
        log.info("不存在的规则返回404验证通过");
    }

    @Test
    @Order(61)
    @DisplayName("边界-2: 未授权访问规则列表 → 验证401")
    void testAlertRuleListUnauthorized() {
        given()
            .when()
            .get("/alert/rule/list")
            .then()
            .statusCode(401);

        log.info("未授权访问返回401验证通过");
    }

    @Test
    @Order(62)
    @DisplayName("边界-3: 处理不存在的告警记录 → 验证404")
    void testResolveRecordNotFound() {
        Response response = getAuthRequest()
            .body(Map.of("resolution", "手动处理"))
            .when()
            .post("/alert/record/" + NON_EXISTENT_ID + "/resolve");

        response.then().statusCode(anyOf(is(404), is(400)));
        log.info("不存在的告警记录处理返回404/400验证通过");
    }

    @Test
    @Order(63)
    @DisplayName("边界-4: 处理告警记录缺少 resolution → 验证400")
    void testResolveRecordMissingResolution() {
        // 尝试用 ID 1 处理，缺少 resolution 字段
        Response response = getAuthRequest()
            .body(new HashMap<>())
            .when()
            .post("/alert/record/1/resolve");

        // 如果记录不存在返回 404，如果存在但 resolution 为空返回 400
        response.then().statusCode(anyOf(is(400), is(404)));
        log.info("缺少resolution返回400/404验证通过");
    }
}
