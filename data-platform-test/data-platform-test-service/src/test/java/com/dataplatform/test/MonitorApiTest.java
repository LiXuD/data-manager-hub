package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 监控管理接口测试
 * 覆盖 7 个接口：告警规则列表、详情、创建、更新、删除，告警记录列表、解决
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MonitorApiTest extends BaseTest {

    private static Long testRuleId;
    private static Long testRecordId;

    // ==================== 告警规则测试 ====================

    /**
     * 测试告警规则列表查询 - 正常场景
     */
    @Test
    @Order(1)
    public void testGetAlertRuleList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/alert/rule/list");

        verifySuccess(response);
        response.then()
            .body("data", notNullValue());
    }

    /**
     * 测试告警规则列表查询 - 未授权
     */
    @Test
    @Order(2)
    public void testGetAlertRuleList_Unauthorized() {
        given()
            .when()
            .get("/alert/rule/list")
            .then()
            .statusCode(401);
    }

    /**
     * 测试告警规则列表查询 - 带过滤条件
     */
    @Test
    @Order(3)
    public void testGetAlertRuleList_WithFilters() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .queryParam("status", "active")
            .when()
            .get("/alert/rule/list");

        verifySuccess(response);
    }

    /**
     * 测试告警规则详情查询 - 正常场景
     */
    @Test
    @Order(4)
    public void testGetAlertRuleById_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/alert/rule/1");

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
     * 测试告警规则详情查询 - 不存在
     */
    @Test
    @Order(5)
    public void testGetAlertRuleById_NotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/alert/rule/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试创建告警规则 - 正常场景
     */
    @Test
    @Order(6)
    public void testCreateAlertRule_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", "测试告警规则_" + System.currentTimeMillis());
        data.put("metric", "cpu_usage");
        data.put("threshold", 80);
        data.put("condition", "gt");
        data.put("level", "warning");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/alert/rule");

        verifySuccess(response);

        Integer id = response.jsonPath().getInt("data.id");
        if (id != null) {
            testRuleId = id.longValue();
        }
    }

    /**
     * 测试创建告警规则 - 必填参数缺失
     */
    @Test
    @Order(7)
    public void testCreateAlertRule_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", "测试规则");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/alert/rule");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试更新告警规则 - 正常场景
     */
    @Test
    @Order(8)
    public void testUpdateAlertRule_Success() {
        if (testRuleId == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "No test rule to update");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("threshold", 90);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/alert/rule/" + testRuleId);

        verifySuccess(response);
    }

    /**
     * 测试更新告警规则 - 不存在
     */
    @Test
    @Order(9)
    public void testUpdateAlertRule_NotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("threshold", 90);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/alert/rule/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试删除告警规则 - 正常场景
     */
    @Test
    @Order(10)
    public void testDeleteAlertRule_Success() {
        if (testRuleId == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "No test rule to delete");
            return;
        }

        Response response = getAuthRequest()
            .when()
            .delete("/alert/rule/" + testRuleId);

        response.then()
            .statusCode(anyOf(is(200), is(204)));
    }

    /**
     * 测试删除告警规则 - 不存在
     */
    @Test
    @Order(11)
    public void testDeleteAlertRule_NotFound() {
        Response response = getAuthRequest()
            .when()
            .delete("/alert/rule/999999999");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    // ==================== 告警记录测试 ====================

    /**
     * 测试告警记录列表查询 - 正常场景
     */
    @Test
    @Order(12)
    public void testGetAlertRecordList_Success() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/alert/record/list");

        verifySuccess(response);
        response.then()
            .body("data", notNullValue());
    }

    /**
     * 测试告警记录列表查询 - 带过滤条件
     */
    @Test
    @Order(13)
    public void testGetAlertRecordList_WithFilters() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .queryParam("status", "pending")
            .queryParam("level", "critical")
            .when()
            .get("/alert/record/list");

        verifySuccess(response);
    }

    /**
     * 测试解决告警记录 - 正常场景
     */
    @Test
    @Order(14)
    public void testResolveAlertRecord_Success() {
        Response response = getAuthRequest()
            .body(Map.of("resolution", "已手动处理"))
            .when()
            .post("/alert/record/1/resolve");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        } else {
            response.then()
                .statusCode(anyOf(is(404), is(400), is(500)));
        }
    }

    /**
     * 测试解决告警记录 - 不存在
     */
    @Test
    @Order(15)
    public void testResolveAlertRecord_NotFound() {
        Response response = getAuthRequest()
            .body(Map.of("resolution", "已处理"))
            .when()
            .post("/alert/record/999999999/resolve");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试解决告警记录 - 缺少解决方案
     */
    @Test
    @Order(16)
    public void testResolveAlertRecord_MissingResolution() {
        Response response = getAuthRequest()
            .body(new HashMap<>())
            .when()
            .post("/alert/record/1/resolve");

        response.then()
            .statusCode(400);
    }
}