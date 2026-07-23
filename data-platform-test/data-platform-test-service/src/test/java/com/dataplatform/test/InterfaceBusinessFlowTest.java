package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 接口管理业务链路测试
 *
 * 模拟前端"接口管理"页面的完整业务流程：
 * 1. 前置依赖：获取数据类型 ID
 * 2. 接口 CRUD 生命周期（含重复 code → 400、缺字段 → 400）
 * 3. 接口状态切换
 * 4. 契约字段管理（创建 → 查询 → 更新 → 删除）
 * 5. 契约快照生成与约束校验
 * 6. 统计查询
 * 7. 删除验证
 * 8. 边界测试
 *
 * 注意：接口重复 code 返回 400（不是 409）
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class InterfaceBusinessFlowTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(InterfaceBusinessFlowTest.class);

    private static Long testInterfaceId;
    private static Long dataTypeId; // 前置依赖

    // ==================== 链路0：前置依赖 ====================

    @Test
    @Order(0)
    @DisplayName("链路0-1: 获取数据类型ID作为前置依赖")
    void testFetchDataTypeId() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 1)
            .when()
            .get("/datatype/list");

        verifySuccess(response);
        String id = response.jsonPath().getString("data[0].id");
        org.junit.jupiter.api.Assertions.assertTrue(id != null, "需要已有数据类型数据");
        dataTypeId = Long.parseLong(id);

        log.info("获取数据类型ID成功: {}", dataTypeId);
    }

    // ==================== 链路1：创建接口 ====================

    @Test
    @Order(1)
    @DisplayName("链路1-1: 查询接口列表 → 验证接口可用")
    void testInterfaceList() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/interface/list");

        verifySuccess(response);
        log.info("接口列表查询成功");
    }

    @Test
    @Order(2)
    @DisplayName("链路1-2: 创建接口 → 提取ID")
    void testCreateInterface() {
        String code = uniqueId("IF");

        Map<String, Object> data = new HashMap<>();
        data.put("interfaceCode", code);
        data.put("interfaceName", "业务链路测试接口");
        data.put("path", "/api/test/" + code);
        data.put("description", "业务链路测试创建的接口");
        if (dataTypeId != null) {
            data.put("dataTypeId", dataTypeId);
        }

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/interface");

        verifySuccess(response);
        testInterfaceId = extractId(response);
        Assertions.assertNotNull(testInterfaceId, "接口创建成功后应返回ID");
        registerDeleteById("/interface/{id}", testInterfaceId);

        log.info("接口创建成功, ID: {}, Code: {}", testInterfaceId, code);
    }

    @Test
    @Order(3)
    @DisplayName("链路1-3: 创建重复 interfaceCode → 验证400")
    void testCreateInterfaceDuplicateCode() {
        org.junit.jupiter.api.Assertions.assertTrue(testInterfaceId != null, "需要测试接口ID");

        Response detail = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId);

        String existingCode = detail.jsonPath().getString("data.interfaceCode");
        org.junit.jupiter.api.Assertions.assertTrue(existingCode != null, "需要已有接口代码");

        Map<String, Object> data = new HashMap<>();
        data.put("interfaceCode", existingCode);
        data.put("interfaceName", "重复代码测试接口");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/interface");

        response.then().statusCode(400);
        log.info("重复interfaceCode返回400验证通过");
    }

    @Test
    @Order(4)
    @DisplayName("链路1-4: 创建接口缺少 interfaceName → 验证400")
    void testCreateInterfaceMissingName() {
        Map<String, Object> data = new HashMap<>();
        data.put("interfaceCode", uniqueId("IF_NO_NAME"));

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/interface");

        response.then().statusCode(400);
        log.info("缺少interfaceName返回400验证通过");
    }

    // ==================== 链路2：查询接口 ====================

    @Test
    @Order(5)
    @DisplayName("链路2-1: 查询接口详情 → 验证创建数据一致")
    void testInterfaceDetail() {
        org.junit.jupiter.api.Assertions.assertTrue(testInterfaceId != null, "需要测试接口ID");

        Response response = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId);

        verifySuccess(response);
        response.then()
            .body("data.id", notNullValue())
            .body("data.interfaceCode", notNullValue())
            .body("data.interfaceName", equalTo("业务链路测试接口"));

        log.info("接口详情验证通过");
    }

    @Test
    @Order(6)
    @DisplayName("链路2-2: 查询不存在的接口 → 验证404")
    void testInterfaceNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/interface/" + NON_EXISTENT_ID);

        response.then().statusCode(404);
        log.info("不存在的接口返回404验证通过");
    }

    @Test
    @Order(7)
    @DisplayName("链路2-3: 按数据类型查询接口列表")
    void testInterfaceByDataType() {
        org.junit.jupiter.api.Assertions.assertTrue(dataTypeId != null, "需要数据类型ID");

        Response response = getAuthRequest()
            .when()
            .get("/interface/by-data-type/" + dataTypeId);

        verifySuccess(response);
        log.info("按数据类型查询接口列表成功");
    }

    // ==================== 链路3：更新接口 ====================

    @Test
    @Order(8)
    @DisplayName("链路3-1: 更新接口信息")
    void testUpdateInterface() {
        org.junit.jupiter.api.Assertions.assertTrue(testInterfaceId != null, "需要测试接口ID");

        Map<String, Object> data = new HashMap<>();
        data.put("interfaceName", "更新后的业务链路测试接口");
        data.put("description", "更新后的描述");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/interface/" + testInterfaceId);

        verifySuccess(response);

        // 验证更新生效
        Response detail = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId);

        verifySuccess(detail);
        detail.then()
            .body("data.interfaceName", equalTo("更新后的业务链路测试接口"));

        log.info("接口更新验证通过");
    }

    // ==================== 链路4：接口状态切换 ====================

    @Test
    @Order(9)
    @DisplayName("链路4-1: 切换接口状态 inactive → active")
    void testInterfaceStatusToggle() {
        org.junit.jupiter.api.Assertions.assertTrue(testInterfaceId != null, "需要测试接口ID");

        // 切换为 inactive
        Response inactiveResp = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/interface/" + testInterfaceId + "/status");

        inactiveResp.then().statusCode(anyOf(is(200), is(204)));

        // 切回 active
        Response activeResp = getAuthRequest()
            .body(Map.of("status", "active"))
            .when()
            .patch("/interface/" + testInterfaceId + "/status");

        activeResp.then().statusCode(anyOf(is(200), is(204)));

        log.info("接口状态切换验证通过 (inactive → active)");
    }

    // ==================== 链路5：契约字段管理 ====================

    @Test
    @Order(10)
    @DisplayName("链路5-1: 查询接口契约 → 验证接口可用")
    void testParamList() {
        org.junit.jupiter.api.Assertions.assertTrue(testInterfaceId != null, "需要测试接口ID");

        Response response = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId + "/contract");

        verifySuccess(response);
        log.info("接口契约查询成功");
    }

    @Test
    @Order(11)
    @DisplayName("链路5-2: 创建请求契约字段")
    void testCreateParam() {
        org.junit.jupiter.api.Assertions.assertTrue(testInterfaceId != null, "需要测试接口ID");

        Map<String, Object> field = new LinkedHashMap<>();
        field.put("paramName", uniqueId("testParam"));
        field.put("paramType", "string");
        field.put("required", true);
        field.put("description", "测试参数");

        Response response = getAuthRequest()
            .body(Map.of("requestFields", List.of(field), "responseFields", List.of()))
            .when()
            .put("/interface/" + testInterfaceId + "/contract");

        verifySuccess(response);
        Assertions.assertNotNull(response.jsonPath().getString("data.requestFields[0].id"),
                "契约字段保存后应返回ID");
        log.info("请求契约字段创建成功");
    }

    @Test
    @Order(12)
    @DisplayName("链路5-3: 更新请求契约字段")
    void testUpdateParam() {
        org.junit.jupiter.api.Assertions.assertTrue(testInterfaceId != null, "需要测试接口ID");

        Map<String, Object> field = new LinkedHashMap<>();
        field.put("paramName", "updatedParam");
        field.put("paramType", "number");
        field.put("description", "更新后的测试参数");

        Response response = getAuthRequest()
            .body(Map.of("requestFields", List.of(field), "responseFields", List.of()))
            .when()
            .put("/interface/" + testInterfaceId + "/contract");

        verifySuccess(response);
        Assertions.assertEquals("updatedParam",
                response.jsonPath().getString("data.requestFields[0].paramName"));
        log.info("请求契约字段更新验证通过");
    }

    @Test
    @Order(13)
    @DisplayName("链路5-4: 清空请求契约字段 → 验证已删除")
    void testDeleteParam() {
        org.junit.jupiter.api.Assertions.assertTrue(testInterfaceId != null, "需要测试接口ID");

        Response response = getAuthRequest()
            .body(Map.of("requestFields", List.of(), "responseFields", List.of()))
            .when()
            .put("/interface/" + testInterfaceId + "/contract");

        verifySuccess(response);
        Assertions.assertTrue(response.jsonPath().getList("data.requestFields").isEmpty());
        log.info("请求契约字段清空验证通过");
    }

    // ==================== 链路6：契约快照与约束 ====================

    @Test
    @Order(14)
    @DisplayName("链路6-1: 获取契约快照 → 验证接口可用")
    void testGetSchema() {
        org.junit.jupiter.api.Assertions.assertTrue(testInterfaceId != null, "需要测试接口ID");

        Response response = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId + "/contract");

        verifySuccess(response);
        Assertions.assertNotNull(response.jsonPath().getString("data.requestSchema"));
        Assertions.assertNotNull(response.jsonPath().getString("data.responseSchema"));
        log.info("获取契约快照成功");
    }

    @Test
    @Order(15)
    @DisplayName("链路6-2: 保存结构化字段 → 验证自动生成快照")
    void testSetSchema() {
        org.junit.jupiter.api.Assertions.assertTrue(testInterfaceId != null, "需要测试接口ID");

        Map<String, Object> requestField = Map.of(
                "paramName", "name", "paramType", "string", "required", true);
        Map<String, Object> responseField = Map.of(
                "paramName", "code", "paramType", "integer", "required", true);

        Response response = getAuthRequest()
            .body(Map.of("requestFields", List.of(requestField),
                    "responseFields", List.of(responseField)))
            .when()
            .put("/interface/" + testInterfaceId + "/contract");

        verifySuccess(response);
        Assertions.assertTrue(response.jsonPath().getString("data.requestSchema").contains("\"name\""));
        Assertions.assertTrue(response.jsonPath().getString("data.responseSchema").contains("\"code\""));
        log.info("结构化字段生成契约快照验证通过");
    }

    @Test
    @Order(16)
    @DisplayName("链路6-3: 保存合法字段约束")
    void testValidateSchema() {
        Map<String, Object> field = Map.of(
                "paramName", "name",
                "paramType", "string",
                "constraintConfig", "{\"minLength\":1}");

        Response response = getAuthRequest()
            .body(Map.of("requestFields", List.of(field), "responseFields", List.of()))
            .when()
            .put("/interface/" + testInterfaceId + "/contract");

        verifySuccess(response);
        log.info("合法字段约束保存成功");
    }

    @Test
    @Order(17)
    @DisplayName("链路6-4: 设置无效字段约束 → 验证400")
    void testSetInvalidSchema() {
        org.junit.jupiter.api.Assertions.assertTrue(testInterfaceId != null, "需要测试接口ID");

        Map<String, Object> field = Map.of(
                "paramName", "name",
                "paramType", "string",
                "constraintConfig", "not-valid-json");

        Response response = getAuthRequest()
            .body(Map.of("requestFields", List.of(field), "responseFields", List.of()))
            .when()
            .put("/interface/" + testInterfaceId + "/contract");

        response.then().statusCode(400);
        log.info("无效字段约束返回400验证通过");
    }

    // ==================== 链路7：统计查询 ====================

    @Test
    @Order(18)
    @DisplayName("链路7-1: 查询接口调用统计 → 验证接口可用")
    void testInterfaceStats() {
        org.junit.jupiter.api.Assertions.assertTrue(testInterfaceId != null, "需要测试接口ID");

        Response response = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId + "/stats");

        verifySuccess(response);
        log.info("接口调用统计查询成功");
    }

    @Test
    @Order(19)
    @DisplayName("链路7-2: 查询接口日统计 → 验证接口可用")
    void testInterfaceStatsDaily() {
        org.junit.jupiter.api.Assertions.assertTrue(testInterfaceId != null, "需要测试接口ID");

        Response response = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId + "/stats/daily");

        verifySuccess(response);
        log.info("接口日统计查询成功");
    }

    // ==================== 链路8：删除接口 ====================

    @Test
    @Order(20)
    @DisplayName("链路8-1: 删除接口 → 验证已删除")
    void testDeleteInterface() {
        org.junit.jupiter.api.Assertions.assertTrue(testInterfaceId != null, "需要测试接口ID");

        Response response = getAuthRequest()
            .when()
            .delete("/interface/" + testInterfaceId);

        response.then().statusCode(anyOf(is(200), is(204)));

        // 验证已删除
        Response check = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId);

        check.then().statusCode(anyOf(is(404), is(400)));

        testInterfaceId = null;
    }

    // ==================== 边界测试 ====================

    @Test
    @Order(60)
    @DisplayName("边界-1: 查询不存在的接口 → 验证404")
    void testInterfaceNotFoundBoundary() {
        Response response = getAuthRequest()
            .when()
            .get("/interface/" + NON_EXISTENT_ID);

        response.then().statusCode(404);
        log.info("不存在的接口返回404验证通过");
    }

    @Test
    @Order(61)
    @DisplayName("边界-2: 修改不存在的接口状态 → 验证404")
    void testUpdateStatusNotFound() {
        Response response = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/interface/" + NON_EXISTENT_ID + "/status");

        response.then().statusCode(anyOf(is(404), is(400)));
        log.info("不存在的接口状态修改返回404/400验证通过");
    }

    @Test
    @Order(62)
    @DisplayName("边界-3: 修改接口状态为无效值 → 验证400")
    void testInterfaceStatusInvalid() {
        // 需要一个存在的接口来测试
        Response listResp = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 1)
            .when()
            .get("/interface/list");

        String interfaceId = listResp.jsonPath().getString("data[0].id");
        org.junit.jupiter.api.Assertions.assertTrue(interfaceId != null, "需要已有接口数据");

        Response response = getAuthRequest()
            .body(Map.of("status", "invalid_status"))
            .when()
            .patch("/interface/" + interfaceId + "/status");

        response.then().statusCode(400);
        log.info("无效状态值返回400验证通过");
    }

    @Test
    @Order(63)
    @DisplayName("边界-4: 给不存在的接口保存契约 → 验证404")
    void testCreateParamInterfaceNotFound() {
        Response response = getAuthRequest()
            .body(Map.of("requestFields", List.of(), "responseFields", List.of()))
            .when()
            .put("/interface/" + NON_EXISTENT_ID + "/contract");

        response.then().statusCode(404);
        log.info("不存在的接口保存契约返回404验证通过");
    }

    @Test
    @Order(64)
    @DisplayName("边界-5: 获取不存在的接口契约 → 验证404")
    void testGetSchemaNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/interface/" + NON_EXISTENT_ID + "/contract");

        response.then().statusCode(404);
        log.info("不存在的接口契约返回404验证通过");
    }
}
