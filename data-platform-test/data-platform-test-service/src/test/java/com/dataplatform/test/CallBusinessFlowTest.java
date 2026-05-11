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
 * Call 模块业务链路测试
 *
 * 模拟前端"调用记录"页面的完整业务流程：
 * 1. 调用记录列表查询（含分页和过滤）
 * 2. 调用记录详情查询
 * 3. POST 分页查询（含参数校验）
 * 4. 调用统计查询
 * 5. CSV 导出
 * 6. 边界测试
 *
 * 注意：CallRecord 是只读的，记录由实际数据查询调用产生，
 * 无法通过 API 直接创建/修改/删除。
 *
 * 基于扫描 CallRecordController + CallRecord Entity 生成
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CallBusinessFlowTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(CallBusinessFlowTest.class);

    // ==================== 链路1：调用记录列表查询 ====================

    @Test
    @Order(1)
    @DisplayName("链路1-1: 查询调用记录列表 → 默认分页")
    void testCallRecordList() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/call-record/list");

        verifySuccess(response);
        log.info("调用记录列表查询成功");
    }

    @Test
    @Order(2)
    @DisplayName("链路1-2: 查询调用记录列表 → 按调用方过滤")
    void testCallRecordListByCaller() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .queryParam("callerId", 1)
            .when()
            .get("/call-record/list");

        verifySuccess(response);
        log.info("调用记录按调用方过滤查询成功");
    }

    @Test
    @Order(3)
    @DisplayName("链路1-3: 查询调用记录列表 → 按成功状态过滤")
    void testCallRecordListBySuccess() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .queryParam("success", true)
            .when()
            .get("/call-record/list");

        verifySuccess(response);
        log.info("调用记录按成功状态过滤查询成功");
    }

    // ==================== 链路2：调用记录详情 ====================

    @Test
    @Order(4)
    @DisplayName("链路2-1: 查询不存在的调用记录 → 验证404")
    void testCallRecordNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/call-record/" + NON_EXISTENT_ID);

        response.then().statusCode(anyOf(is(404), is(400)));
        log.info("调用记录不存在校验通过");
    }

    // ==================== 链路3：POST 分页查询 ====================

    @Test
    @Order(5)
    @DisplayName("链路3-1: POST 分页查询调用记录")
    void testCallRecordQuery() {
        Map<String, Object> data = new HashMap<>();
        data.put("page", 1);
        data.put("pageSize", 10);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/call-record/query");

        verifySuccess(response);
        log.info("POST 分页查询调用记录成功");
    }

    @Test
    @Order(6)
    @DisplayName("链路3-2: POST 分页查询 → page<1 → 验证400")
    void testCallRecordQueryInvalidPage() {
        Map<String, Object> data = new HashMap<>();
        data.put("page", 0);
        data.put("pageSize", 10);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/call-record/query");

        response.then().statusCode(400);
        log.info("无效 page 参数校验通过");
    }

    @Test
    @Order(7)
    @DisplayName("链路3-3: POST 分页查询 → pageSize>100 → 验证400")
    void testCallRecordQueryInvalidPageSize() {
        Map<String, Object> data = new HashMap<>();
        data.put("page", 1);
        data.put("pageSize", 200);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/call-record/query");

        response.then().statusCode(400);
        log.info("无效 pageSize 参数校验通过");
    }

    // ==================== 链路4：调用统计 ====================

    @Test
    @Order(8)
    @DisplayName("链路4-1: 查询调用统计")
    void testCallRecordStats() {
        Response response = getAuthRequest()
            .when()
            .get("/call-record/stats");

        verifySuccess(response);
        log.info("调用统计查询成功");
    }

    // ==================== 链路5：CSV 导出 ====================

    @Test
    @Order(9)
    @DisplayName("链路5-1: 导出调用记录 CSV")
    void testCallRecordExport() {
        Response response = getAuthRequest()
            .when()
            .get("/call-record/export");

        // CSV 导出可能返回 200 或 204（无数据时）
        response.then().statusCode(anyOf(is(200), is(204)));
        log.info("调用记录 CSV 导出成功, status: {}", response.getStatusCode());
    }

    // ==================== 链路6：边界测试 ====================

    @Test
    @Order(20)
    @DisplayName("边界-1: 未授权访问调用记录列表 → 验证401")
    void testCallRecordListUnauthorized() {
        given()
            .when()
            .get("/call-record/list")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(21)
    @DisplayName("边界-2: POST 分页查询 → pageSize<1 → 验证400")
    void testCallRecordQueryNegativePageSize() {
        Map<String, Object> data = new HashMap<>();
        data.put("page", 1);
        data.put("pageSize", 0);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/call-record/query");

        response.then().statusCode(400);
        log.info("负数 pageSize 参数校验通过");
    }
}
