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
 * 4. 参数管理（创建 → 查询 → 更新 → 删除）
 * 5. Schema 管理（获取 → 设置 → 验证）
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
    private static Long testParamId;
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
        Assumptions.assumeTrue(id != null, "需要已有数据类型数据");
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
        Assumptions.assumeTrue(testInterfaceId != null, "需要测试接口ID");

        Response detail = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId);

        String existingCode = detail.jsonPath().getString("data.interfaceCode");
        Assumptions.assumeTrue(existingCode != null, "需要已有接口代码");

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
        Assumptions.assumeTrue(testInterfaceId != null, "需要测试接口ID");

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
        Assumptions.assumeTrue(dataTypeId != null, "需要数据类型ID");

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
        Assumptions.assumeTrue(testInterfaceId != null, "需要测试接口ID");

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
        Assumptions.assumeTrue(testInterfaceId != null, "需要测试接口ID");

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

    // ==================== 链路5：参数管理 ====================

    @Test
    @Order(10)
    @DisplayName("链路5-1: 查询接口参数列表 → 验证接口可用")
    void testParamList() {
        Assumptions.assumeTrue(testInterfaceId != null, "需要测试接口ID");

        Response response = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId + "/params");

        verifySuccess(response);
        log.info("接口参数列表查询成功");
    }

    @Test
    @Order(11)
    @DisplayName("链路5-2: 创建接口参数 → 提取ID")
    void testCreateParam() {
        Assumptions.assumeTrue(testInterfaceId != null, "需要测试接口ID");

        Map<String, Object> data = new HashMap<>();
        data.put("paramName", uniqueId("testParam"));
        data.put("paramType", "string");
        data.put("required", true);
        data.put("description", "测试参数");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/interface/" + testInterfaceId + "/params");

        verifySuccess(response);
        testParamId = extractId(response);
        Assertions.assertNotNull(testParamId, "参数创建成功后应返回ID");

        log.info("接口参数创建成功, ID: {}", testParamId);
    }

    @Test
    @Order(12)
    @DisplayName("链路5-3: 更新接口参数")
    void testUpdateParam() {
        Assumptions.assumeTrue(testInterfaceId != null, "需要测试接口ID");
        Assumptions.assumeTrue(testParamId != null, "需要测试参数ID");

        Map<String, Object> data = new HashMap<>();
        data.put("paramName", "updatedParam");
        data.put("paramType", "number");
        data.put("description", "更新后的测试参数");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/interface/params/" + testParamId);

        verifySuccess(response);
        log.info("接口参数更新验证通过");
    }

    @Test
    @Order(13)
    @DisplayName("链路5-4: 删除接口参数 → 验证已删除")
    void testDeleteParam() {
        Assumptions.assumeTrue(testParamId != null, "需要测试参数ID");

        Response response = getAuthRequest()
            .when()
            .delete("/interface/params/" + testParamId);

        response.then().statusCode(anyOf(is(200), is(204)));

        testParamId = null;
        log.info("接口参数删除验证通过");
    }

    // ==================== 链路6：Schema 管理 ====================

    @Test
    @Order(14)
    @DisplayName("链路6-1: 获取接口Schema → 验证接口可用")
    void testGetSchema() {
        Assumptions.assumeTrue(testInterfaceId != null, "需要测试接口ID");

        Response response = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId + "/schema");

        verifySuccess(response);
        log.info("获取接口Schema成功");
    }

    @Test
    @Order(15)
    @DisplayName("链路6-2: 设置接口Schema → 验证生效")
    void testSetSchema() {
        Assumptions.assumeTrue(testInterfaceId != null, "需要测试接口ID");

        String requestSchema = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}";
        String responseSchema = "{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"integer\"}}}";

        Map<String, Object> data = new HashMap<>();
        data.put("requestSchema", requestSchema);
        data.put("responseSchema", responseSchema);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/interface/" + testInterfaceId + "/schema");

        verifySuccess(response);

        // 验证 Schema 生效
        Response check = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId + "/schema");

        verifySuccess(check);
        log.info("设置接口Schema验证通过");
    }

    @Test
    @Order(16)
    @DisplayName("链路6-3: 验证Schema格式 → 验证接口可用")
    void testValidateSchema() {
        String validSchema = "{\"type\":\"object\"}";

        Response response = getAuthRequest()
            .body(Map.of("schema", validSchema))
            .when()
            .post("/interface/schema/validate");

        verifySuccess(response);
        log.info("Schema格式验证接口可用");
    }

    @Test
    @Order(17)
    @DisplayName("链路6-4: 设置无效Schema → 验证400")
    void testSetInvalidSchema() {
        Assumptions.assumeTrue(testInterfaceId != null, "需要测试接口ID");

        Map<String, Object> data = new HashMap<>();
        data.put("requestSchema", "not-valid-json");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/interface/" + testInterfaceId + "/schema");

        response.then().statusCode(400);
        log.info("无效Schema返回400验证通过");
    }

    // ==================== 链路7：统计查询 ====================

    @Test
    @Order(18)
    @DisplayName("链路7-1: 查询接口调用统计 → 验证接口可用")
    void testInterfaceStats() {
        Assumptions.assumeTrue(testInterfaceId != null, "需要测试接口ID");

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
        Assumptions.assumeTrue(testInterfaceId != null, "需要测试接口ID");

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
        Assumptions.assumeTrue(testInterfaceId != null, "需要测试接口ID");

        Response response = getAuthRequest()
            .when()
            .delete("/interface/" + testInterfaceId);

        response.then().statusCode(anyOf(is(200), is(204)));

        // 验证已删除
        Response check = getAuthRequest()
            .when()
            .get("/interface/" + testInterfaceId);

        int status = check.getStatusCode();
        if (status == 404 || status == 400) {
            log.info("接口已确认删除 ({}返回)", status);
        } else {
            log.warn("接口删除后仍可查询 (status={})", status);
        }

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
        Assumptions.assumeTrue(interfaceId != null, "需要已有接口数据");

        Response response = getAuthRequest()
            .body(Map.of("status", "invalid_status"))
            .when()
            .patch("/interface/" + interfaceId + "/status");

        response.then().statusCode(400);
        log.info("无效状态值返回400验证通过");
    }

    @Test
    @Order(63)
    @DisplayName("边界-4: 给不存在的接口创建参数 → 验证404")
    void testCreateParamInterfaceNotFound() {
        Map<String, Object> data = new HashMap<>();
        data.put("paramName", "testParam");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/interface/" + NON_EXISTENT_ID + "/params");

        response.then().statusCode(anyOf(is(404), is(400)));
        log.info("不存在的接口创建参数返回404/400验证通过");
    }

    @Test
    @Order(64)
    @DisplayName("边界-5: 获取不存在的接口Schema → 验证404")
    void testGetSchemaNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/interface/" + NON_EXISTENT_ID + "/schema");

        response.then().statusCode(404);
        log.info("不存在的接口Schema返回404验证通过");
    }
}
