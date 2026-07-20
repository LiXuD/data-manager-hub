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
 * Audit (操作日志) 模块业务链路测试
 *
 * 模拟前端"操作日志"页面的完整业务流程：
 * 1. 操作日志列表查询（含分页和过滤）
 * 2. 操作日志详情查询
 * 3. 内部日志写入（Internal API）
 * 4. 边界测试
 *
 * 注意：OperationLog 是只读的，记录由 @OperationLog 注解自动产生，
 * 前端只能查询和筛选。内部写入接口供其他服务 Feign 调用。
 *
 * 基于扫描 LogController + InternalLogController + OperationLog Entity 生成
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuditBusinessFlowTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(AuditBusinessFlowTest.class);

    // ==================== 链路1：操作日志列表查询 ====================

    @Test
    @Order(1)
    @DisplayName("链路1-1: 查询操作日志列表 → 默认分页")
    void testAuditLogList() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/log/list");

        verifySuccess(response);
        log.info("操作日志列表查询成功");
    }

    @Test
    @Order(2)
    @DisplayName("链路1-2: 查询操作日志列表 → 按模块过滤")
    void testAuditLogListByModule() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .queryParam("module", "厂商管理")
            .when()
            .get("/log/list");

        verifySuccess(response);
        log.info("操作日志按模块过滤查询成功");
    }

    @Test
    @Order(3)
    @DisplayName("链路1-3: 查询操作日志列表 → 按关键词搜索")
    void testAuditLogListByKeyword() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .queryParam("keyword", "admin")
            .when()
            .get("/log/list");

        verifySuccess(response);
        log.info("操作日志按关键词搜索成功");
    }

    @Test
    @Order(4)
    @DisplayName("链路1-4: 查询操作日志列表 → 按操作名过滤")
    void testAuditLogListByOperation() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .queryParam("operation", "新增")
            .when()
            .get("/log/list");

        verifySuccess(response);
        log.info("操作日志按操作名过滤查询成功");
    }

    // ==================== 链路2：操作日志详情 ====================

    @Test
    @Order(5)
    @DisplayName("链路2-1: 查询不存在的操作日志 → 验证404")
    void testAuditLogNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/log/" + NON_EXISTENT_ID);

        response.then().statusCode(anyOf(is(404), is(400)));
        log.info("操作日志不存在校验通过");
    }

    // ==================== 链路3：内部日志写入 ====================

    @Test
    @Order(6)
    @DisplayName("链路3-1: 经网关访问内部接口 → 验证拒绝")
    void testInternalLogBlockedByGateway() {
        given()
            .contentType("application/json")
            .body(Map.of("module", "测试模块"))
            .when()
            .post(GATEWAY_URL + "/internal/v1/governance/logs")
            .then()
            .statusCode(404);
    }

    // ==================== 链路4：联动验证 ====================

    @Test
    @Order(7)
    @DisplayName("链路4-1: 执行一次操作 → 验证日志被记录")
    void testAuditLogCreatedFromOperation() {
        // 先记下当前日志总数
        Response beforeResp = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 1)
            .when()
            .get("/log/list");

        verifySuccess(beforeResp);
        int beforeTotal = beforeResp.jsonPath().getInt("total");

        // 执行一个会产生操作日志的操作（创建一个临时厂商然后删除）
        Map<String, Object> vendorData = new HashMap<>();
        vendorData.put("vendorCode", "AUDIT_TEST_" + System.currentTimeMillis());
        vendorData.put("vendorName", "审计测试厂商");

        Response createResp = getAuthRequest()
            .body(vendorData)
            .when()
            .post("/vendor");

        verifySuccess(createResp);
        Long vendorId = extractId(createResp);
        Assertions.assertNotNull(vendorId, "临时厂商创建后应返回ID");
        getAuthRequest().when().delete("/vendor/" + vendorId).then().statusCode(200);

        try { Thread.sleep(2000); } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Assertions.fail("等待异步日志时被中断", exception);
        }

        Response afterResp = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 1)
            .when()
            .get("/log/list");

        verifySuccess(afterResp);
        int afterTotal = afterResp.jsonPath().getInt("total");
        org.junit.jupiter.api.Assertions.assertTrue(afterTotal > beforeTotal,
            "操作后日志总数应增加: before=" + beforeTotal + ", after=" + afterTotal);
        log.info("操作日志联动验证通过: before={}, after={}", beforeTotal, afterTotal);
    }

    // ==================== 链路5：边界测试 ====================

    @Test
    @Order(20)
    @DisplayName("边界-1: 未授权访问操作日志列表 → 验证401")
    void testAuditLogListUnauthorized() {
        given()
            .when()
            .get("/log/list")
            .then()
            .statusCode(401);
    }
}
