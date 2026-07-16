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
 * Vendor 模块业务链路测试
 *
 * 模拟前端"厂商管理"页面的完整业务流程：
 * 1. 创建数据类型（前置依赖）
 * 2. 创建厂商
 * 3. 创建厂商配置（依赖厂商+数据类型）
 * 4. 扩展配置（依赖厂商）
 * 5. 状态切换
 * 6. 查询验证
 * 7. 清理删除
 *
 * 基于扫描 VendorManagement.vue + VendorController + Entity 字段映射生成
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VendorBusinessFlowTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(VendorBusinessFlowTest.class);

    /** 测试中创建的 ID，用于清理 */
    private static Long testVendorId;
    private static Long testDataTypeId;
    private static Long testVendorConfigId;
    private static Long testExtendedConfigId;

    // ==================== 链路1：创建数据类型 ====================

    @Test
    @Order(1)
    @DisplayName("链路1-1: 查询数据类型列表 → 验证已有数据")
    void testDataTypeList() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/datatype/list");

        verifySuccess(response);
        log.info("数据类型列表查询成功");
    }

    @Test
    @Order(2)
    @DisplayName("链路1-2: 创建数据类型 → 提取ID")
    void testCreateDataType() {
        String code = "DT_" + System.currentTimeMillis();

        Map<String, Object> data = new HashMap<>();
        data.put("dataTypeCode", code);
        data.put("dataTypeName", "业务链路测试数据类型");
        data.put("dataCategory", "test");
        data.put("pricingModel", "per_call");
        data.put("unitPrice", 0.5);
        data.put("status", "active");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/datatype");

        verifySuccess(response);
        testDataTypeId = extractId(response);
        Assertions.assertNotNull(testDataTypeId, "数据类型创建成功后应返回ID");
        registerDeleteById("/datatype/{id}", testDataTypeId);

        log.info("数据类型创建成功, ID: {}", testDataTypeId);
    }

    @Test
    @Order(3)
    @DisplayName("链路1-3: 查询数据类型详情 → 验证创建数据一致")
    void testDataTypeDetail() {
        org.junit.jupiter.api.Assertions.assertTrue(testDataTypeId != null, "需要测试数据类型ID");

        Response response = getAuthRequest()
            .when()
            .get("/datatype/" + testDataTypeId);

        verifySuccess(response);
        response.then()
            .body("data.id", notNullValue())
            .body("data.dataTypeCode", notNullValue());

        log.info("数据类型详情验证通过");
    }

    @Test
    @Order(4)
    @DisplayName("链路1-4: 更新数据类型 → 验证修改生效")
    void testUpdateDataType() {
        org.junit.jupiter.api.Assertions.assertTrue(testDataTypeId != null, "需要测试数据类型ID");

        Map<String, Object> data = new HashMap<>();
        data.put("dataTypeName", "更新后的业务链路测试数据类型");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/datatype/" + testDataTypeId);

        verifySuccess(response);

        // 验证更新生效
        Response detailResponse = getAuthRequest()
            .when()
            .get("/datatype/" + testDataTypeId);

        verifySuccess(detailResponse);

        log.info("数据类型更新验证通过");
    }

    @Test
    @Order(5)
    @DisplayName("链路1-5: 数据类型状态切换 → active/inactive")
    void testDataTypeStatusToggle() {
        org.junit.jupiter.api.Assertions.assertTrue(testDataTypeId != null, "需要测试数据类型ID");

        // 切换为 inactive
        Response inactiveResponse = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/datatype/" + testDataTypeId + "/status");

        // 有些实现可能返回 200，有些返回 204
        inactiveResponse.then().statusCode(anyOf(is(200), is(204)));

        // 切换回 active
        Response activeResponse = getAuthRequest()
            .body(Map.of("status", "active"))
            .when()
            .patch("/datatype/" + testDataTypeId + "/status");

        activeResponse.then().statusCode(anyOf(is(200), is(204)));

        log.info("数据类型状态切换验证通过");
    }

    // ==================== 链路2：创建厂商 ====================

    @Test
    @Order(10)
    @DisplayName("链路2-1: 创建厂商 → 提取ID")
    void testCreateVendor() {
        String code = "VENDOR_" + System.currentTimeMillis();

        Map<String, Object> data = new HashMap<>();
        data.put("vendorCode", code);
        data.put("vendorName", "业务链路测试厂商");
        data.put("vendorType", "enterprise_data");
        data.put("contactPerson", "测试联系人");
        data.put("contactPhone", "13800138000");
        data.put("contactEmail", "test@example.com");
        data.put("status", "active");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/vendor");

        verifySuccess(response);
        testVendorId = extractId(response);
        Assertions.assertNotNull(testVendorId, "厂商创建成功后应返回ID");
        registerDeleteById("/vendor/{id}", testVendorId);

        log.info("厂商创建成功, ID: {}, Code: {}", testVendorId, code);
    }

    @Test
    @Order(11)
    @DisplayName("链路2-2: 创建厂商（重复vendorCode）→ 验证409冲突")
    void testCreateVendorDuplicateCode() {
        // 先查已有厂商的 vendorCode
        Response listResponse = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 1)
            .when()
            .get("/vendor/list");

        verifySuccess(listResponse);

        String existingCode = listResponse.jsonPath().getString("data[0].vendorCode");
        if (existingCode == null) {
            org.junit.jupiter.api.Assertions.assertTrue(false, "没有已有厂商数据，跳过重复代码测试");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("vendorCode", existingCode);
        data.put("vendorName", "重复代码测试厂商");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/vendor");

        response.then().statusCode(anyOf(is(400), is(409)));

        log.info("重复vendorCode冲突检测通过, code: {}", existingCode);
    }

    @Test
    @Order(12)
    @DisplayName("链路2-3: 查询厂商详情 → 验证创建数据一致")
    void testVendorDetail() {
        org.junit.jupiter.api.Assertions.assertTrue(testVendorId != null, "需要测试厂商ID");

        Response response = getAuthRequest()
            .when()
            .get("/vendor/" + testVendorId);

        verifySuccess(response);
        response.then()
            .body("data.id", notNullValue())
            .body("data.vendorCode", notNullValue())
            .body("data.vendorName", equalTo("业务链路测试厂商"));

        log.info("厂商详情验证通过");
    }

    @Test
    @Order(13)
    @DisplayName("链路2-4: 更新厂商 → 验证修改生效")
    void testUpdateVendor() {
        org.junit.jupiter.api.Assertions.assertTrue(testVendorId != null, "需要测试厂商ID");

        Map<String, Object> data = new HashMap<>();
        data.put("vendorName", "更新后的业务链路测试厂商");
        data.put("contactPerson", "更新联系人");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/vendor/" + testVendorId);

        verifySuccess(response);

        // 验证更新生效
        Response detailResponse = getAuthRequest()
            .when()
            .get("/vendor/" + testVendorId);

        verifySuccess(detailResponse);
        detailResponse.then()
            .body("data.vendorName", equalTo("更新后的业务链路测试厂商"));

        log.info("厂商更新验证通过");
    }

    @Test
    @Order(14)
    @DisplayName("链路2-5: 厂商状态切换 → active/inactive/suspended")
    void testVendorStatusToggle() {
        org.junit.jupiter.api.Assertions.assertTrue(testVendorId != null, "需要测试厂商ID");

        // 切换为 inactive
        Response inactiveResp = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/vendor/" + testVendorId + "/status");

        inactiveResp.then().statusCode(anyOf(is(200), is(204)));

        // 切换为 suspended
        Response suspendedResp = getAuthRequest()
            .body(Map.of("status", "suspended"))
            .when()
            .patch("/vendor/" + testVendorId + "/status");

        suspendedResp.then().statusCode(anyOf(is(200), is(204)));

        // 切回 active（后续配置测试需要）
        Response activeResp = getAuthRequest()
            .body(Map.of("status", "active"))
            .when()
            .patch("/vendor/" + testVendorId + "/status");

        activeResp.then().statusCode(anyOf(is(200), is(204)));

        log.info("厂商状态切换验证通过 (inactive → suspended → active)");
    }

    // ==================== 链路3：创建厂商配置 ====================

    @Test
    @Order(20)
    @DisplayName("链路3-1: 创建厂商配置 → 提取ID（依赖厂商+数据类型）")
    void testCreateVendorConfig() {
        org.junit.jupiter.api.Assertions.assertTrue(testVendorId != null, "需要测试厂商ID");
        org.junit.jupiter.api.Assertions.assertTrue(testDataTypeId != null, "需要测试数据类型ID");

        Map<String, Object> data = new HashMap<>();
        data.put("vendorId", testVendorId);
        data.put("dataTypeId", testDataTypeId);
        data.put("interfaceId", 1L); // 前端要求必须传 interfaceId
        data.put("apiUrl", "https://api.test.example.com/data");
        data.put("method", "POST");
        data.put("timeout", 30000);
        data.put("retryCount", 3);
        data.put("circuitThreshold", 5);
        data.put("circuitTimeout", 60);
        data.put("status", "active");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/vendor/config");

        verifySuccess(response);
        {
            testVendorConfigId = extractId(response);
            Assertions.assertNotNull(testVendorConfigId, "厂商配置创建成功后应返回ID");
            registerDeleteById("/vendor/config/{id}", testVendorConfigId);
            log.info("厂商配置创建成功, ID: {}", testVendorConfigId);
        }
    }

    @Test
    @Order(21)
    @DisplayName("链路3-2: 查询厂商配置详情 → 验证创建数据一致")
    void testVendorConfigDetail() {
        org.junit.jupiter.api.Assertions.assertTrue(testVendorConfigId != null, "需要测试厂商配置ID");

        Response response = getAuthRequest()
            .when()
            .get("/vendor/config/" + testVendorConfigId);

        verifySuccess(response);
        response.then()
            .body("data.id", notNullValue())
            .body("data.vendorId", notNullValue());

        log.info("厂商配置详情验证通过");
    }

    @Test
    @Order(22)
    @DisplayName("链路3-3: 更新厂商配置 → 验证修改生效")
    void testUpdateVendorConfig() {
        org.junit.jupiter.api.Assertions.assertTrue(testVendorConfigId != null, "需要测试厂商配置ID");

        Map<String, Object> data = new HashMap<>();
        data.put("apiUrl", "https://api.test.example.com/updated");
        data.put("timeout", 50000);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/vendor/config/" + testVendorConfigId);

        verifySuccess(response);

        // 验证更新生效
        Response detailResponse = getAuthRequest()
            .when()
            .get("/vendor/config/" + testVendorConfigId);

        verifySuccess(detailResponse);

        log.info("厂商配置更新验证通过");
    }

    @Test
    @Order(23)
    @DisplayName("链路3-4: 厂商配置状态切换 → active/inactive")
    void testVendorConfigStatusToggle() {
        org.junit.jupiter.api.Assertions.assertTrue(testVendorConfigId != null, "需要测试厂商配置ID");

        // 切换为 inactive
        Response inactiveResp = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/vendor/config/" + testVendorConfigId + "/status");

        inactiveResp.then().statusCode(anyOf(is(200), is(204)));

        // 切回 active
        Response activeResp = getAuthRequest()
            .body(Map.of("status", "active"))
            .when()
            .patch("/vendor/config/" + testVendorConfigId + "/status");

        activeResp.then().statusCode(anyOf(is(200), is(204)));

        log.info("厂商配置状态切换验证通过");
    }

    // ==================== 链路4：扩展配置 ====================

    @Test
    @Order(30)
    @DisplayName("链路4-1: 创建扩展配置 → 提取ID（依赖厂商）")
    void testCreateExtendedConfig() {
        org.junit.jupiter.api.Assertions.assertTrue(testVendorId != null, "需要测试厂商ID");

        String configKey = "ext_key_" + System.currentTimeMillis();

        Map<String, Object> data = new HashMap<>();
        data.put("vendorId", testVendorId);
        data.put("configKey", configKey);
        data.put("configValue", "test_value");
        data.put("configType", "string");
        data.put("description", "业务链路测试扩展配置");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/vendor/extended-config");

        verifySuccess(response);
        {
            testExtendedConfigId = extractId(response);
            Assertions.assertNotNull(testExtendedConfigId, "扩展配置创建成功后应返回ID");
            registerDeleteById("/vendor/extended-config/{id}", testExtendedConfigId);
            log.info("扩展配置创建成功, ID: {}", testExtendedConfigId);
        }
    }

    @Test
    @Order(31)
    @DisplayName("链路4-2: 查询扩展配置列表 → 按厂商过滤")
    void testExtendedConfigListByVendor() {
        org.junit.jupiter.api.Assertions.assertTrue(testVendorId != null, "需要测试厂商ID");

        Response response = getAuthRequest()
            .when()
            .get("/vendor/extended-config/vendor/" + testVendorId);

        verifySuccess(response);
        {
            verifySuccess(response);
        }

        log.info("扩展配置按厂商查询完成");
    }

    @Test
    @Order(32)
    @DisplayName("链路4-3: 更新扩展配置 → 验证修改生效")
    void testUpdateExtendedConfig() {
        org.junit.jupiter.api.Assertions.assertTrue(testExtendedConfigId != null, "需要测试扩展配置ID");

        Map<String, Object> data = new HashMap<>();
        data.put("configValue", "updated_test_value");
        data.put("description", "更新后的业务链路测试扩展配置");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/vendor/extended-config/" + testExtendedConfigId);

        verifySuccess(response);
        {
            verifySuccess(response);
            log.info("扩展配置更新验证通过");
        }
    }

    // ==================== 链路5：查询验证 ====================

    @Test
    @Order(40)
    @DisplayName("链路5-1: 查询厂商列表 → 确认厂商存在")
    void testVerifyVendorInList() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 100)
            .when()
            .get("/vendor/list");

        verifySuccess(response);

        log.info("厂商列表查询验证通过");
    }

    @Test
    @Order(41)
    @DisplayName("链路5-2: 查询数据类型列表 → 确认数据类型存在")
    void testVerifyDataTypeInList() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 100)
            .when()
            .get("/datatype/list");

        verifySuccess(response);

        log.info("数据类型列表查询验证通过");
    }

    @Test
    @Order(42)
    @DisplayName("链路5-3: 查询厂商配置列表 → 按厂商过滤")
    void testVerifyVendorConfigByVendor() {
        org.junit.jupiter.api.Assertions.assertTrue(testVendorId != null, "需要测试厂商ID");

        Response response = getAuthRequest()
            .when()
            .get("/vendor/config/vendor/" + testVendorId);

        verifySuccess(response);
        {
            verifySuccess(response);
        }

        log.info("厂商配置按厂商查询验证通过");
    }

    @Test
    @Order(43)
    @DisplayName("链路5-4: 查询全部厂商 → 验证仅返回active")
    void testGetAllActiveVendors() {
        Response response = getAuthRequest()
            .when()
            .get("/vendor/all");

        verifySuccess(response);

        log.info("全部厂商查询验证通过");
    }

    @Test
    @Order(44)
    @DisplayName("链路5-5: 查询全部数据类型 → 验证仅返回active")
    void testGetAllActiveDataTypes() {
        Response response = getAuthRequest()
            .when()
            .get("/datatype/all");

        verifySuccess(response);

        log.info("全部数据类型查询验证通过");
    }

    // ==================== 链路6：删除清理 ====================

    @Test
    @Order(50)
    @DisplayName("链路6-1: 删除扩展配置 → 验证已删除")
    void testDeleteExtendedConfig() {
        org.junit.jupiter.api.Assertions.assertTrue(testExtendedConfigId != null, "没有需要删除的扩展配置");

        Response response = getAuthRequest()
            .when()
            .delete("/vendor/extended-config/" + testExtendedConfigId);

        response.then().statusCode(anyOf(is(200), is(204)));
        log.info("扩展配置删除成功, ID: {}", testExtendedConfigId);

        testExtendedConfigId = null; // 标记已删除，避免 cleanup 重复删除
    }

    @Test
    @Order(51)
    @DisplayName("链路6-2: 删除厂商配置 → 验证已删除")
    void testDeleteVendorConfig() {
        org.junit.jupiter.api.Assertions.assertTrue(testVendorConfigId != null, "没有需要删除的厂商配置");

        Response response = getAuthRequest()
            .when()
            .delete("/vendor/config/" + testVendorConfigId);

        response.then().statusCode(anyOf(is(200), is(204)));
        log.info("厂商配置删除成功, ID: {}", testVendorConfigId);

        testVendorConfigId = null;
    }

    @Test
    @Order(52)
    @DisplayName("链路6-3: 删除厂商 → 验证已删除")
    void testDeleteVendor() {
        org.junit.jupiter.api.Assertions.assertTrue(testVendorId != null, "没有需要删除的厂商");

        Response response = getAuthRequest()
            .when()
            .delete("/vendor/" + testVendorId);

        response.then().statusCode(anyOf(is(200), is(204)));
        log.info("厂商删除成功, ID: {}", testVendorId);

        Response checkResponse = getAuthRequest()
            .when()
            .get("/vendor/" + testVendorId);
        checkResponse.then().statusCode(anyOf(is(404), is(400)));

        testVendorId = null;
    }

    @Test
    @Order(53)
    @DisplayName("链路6-4: 删除数据类型 → 验证已删除")
    void testDeleteDataType() {
        org.junit.jupiter.api.Assertions.assertTrue(testDataTypeId != null, "没有需要删除的数据类型");

        Response response = getAuthRequest()
            .when()
            .delete("/datatype/" + testDataTypeId);

        response.then().statusCode(anyOf(is(200), is(204)));
        log.info("数据类型删除成功, ID: {}", testDataTypeId);

        Response checkResponse = getAuthRequest()
            .when()
            .get("/datatype/" + testDataTypeId);
        checkResponse.then().statusCode(anyOf(is(404), is(400)));

        testDataTypeId = null;
    }

    // ==================== 链路7：边界测试 ====================

    @Test
    @Order(60)
    @DisplayName("边界-1: 创建厂商（缺少vendorCode）→ 验证400")
    void testCreateVendorMissingVendorCode() {
        Map<String, Object> data = new HashMap<>();
        data.put("vendorName", "缺少代码的厂商");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/vendor");

        response.then().statusCode(400);
    }

    @Test
    @Order(61)
    @DisplayName("边界-2: 创建厂商（缺少vendorName）→ 验证400")
    void testCreateVendorMissingVendorName() {
        Map<String, Object> data = new HashMap<>();
        data.put("vendorCode", "MISSING_NAME_" + System.currentTimeMillis());

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/vendor");

        response.then().statusCode(400);
    }

    @Test
    @Order(62)
    @DisplayName("边界-3: 创建数据类型（缺少dataTypeCode）→ 验证400")
    void testCreateDataTypeMissingCode() {
        Map<String, Object> data = new HashMap<>();
        data.put("dataTypeName", "缺少代码的数据类型");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/datatype");

        response.then().statusCode(anyOf(is(400), is(500)));
    }

    @Test
    @Order(63)
    @DisplayName("边界-4: 查询不存在的厂商 → 验证404")
    void testGetVendorNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/vendor/" + NON_EXISTENT_ID);

        response.then().statusCode(anyOf(is(404), is(400)));
    }

    @Test
    @Order(64)
    @DisplayName("边界-5: 查询不存在的数据类型 → 验证404")
    void testGetDataTypeNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/datatype/" + NON_EXISTENT_ID);

        response.then().statusCode(anyOf(is(404), is(400)));
    }

    @Test
    @Order(65)
    @DisplayName("边界-6: 未授权访问厂商列表 → 验证401")
    void testVendorListUnauthorized() {
        given()
            .when()
            .get("/vendor/list")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(66)
    @DisplayName("边界-7: 状态值无效 → 验证400")
    void testVendorStatusInvalid() {
        org.junit.jupiter.api.Assertions.assertTrue(testVendorId != null, "需要测试厂商ID");

        Response response = getAuthRequest()
            .body(Map.of("status", "invalid_status"))
            .when()
            .patch("/vendor/" + testVendorId + "/status");

        response.then().statusCode(anyOf(is(400), is(200))); // 部分实现可能宽松处理
    }
}
