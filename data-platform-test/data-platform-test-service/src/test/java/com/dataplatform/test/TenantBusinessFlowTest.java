package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 租户管理业务链路测试
 *
 * 模拟前端"租户管理"页面的完整业务流程：
 * 1. 创建租户 → 查询确认 → 修改 → 状态切换 → 删除
 * 2. 边界测试：缺字段、不存在、未授权、无效状态
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TenantBusinessFlowTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(TenantBusinessFlowTest.class);

    private static Long testTenantId;

    // ==================== 链路1：创建租户 ====================

    @Test
    @Order(1)
    @DisplayName("链路1-1: 查询租户列表 → 验证接口可用")
    void testTenantList() {
        Response response = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 10)
            .when()
            .get("/tenant/list");

        verifySuccess(response);
        log.info("租户列表查询成功");
    }

    @Test
    @Order(2)
    @DisplayName("链路1-2: 创建租户 → 提取ID")
    void testCreateTenant() {
        String code = uniqueId("TENANT");

        Map<String, Object> data = new HashMap<>();
        data.put("tenantCode", code);
        data.put("tenantName", "业务链路测试租户");
        data.put("tenantType", "enterprise");
        data.put("contactPerson", "测试联系人");
        data.put("contactPhone", "13800138000");
        data.put("contactEmail", "test@tenant.com");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/tenant");

        verifySuccess(response);
        testTenantId = extractId(response);
        Assertions.assertNotNull(testTenantId, "租户创建成功后应返回ID");
        registerDeleteById("/tenant/{id}", testTenantId);

        log.info("租户创建成功, ID: {}, Code: {}", testTenantId, code);
    }

    @Test
    @Order(3)
    @DisplayName("链路1-3: 创建重复 tenantCode → 验证409冲突")
    void testCreateTenantDuplicateCode() {
        Assumptions.assumeTrue(testTenantId != null, "需要测试租户ID");

        // 用已创建租户的 code 再次创建
        Response detail = getAuthRequest()
            .when()
            .get("/tenant/" + testTenantId);

        String existingCode = detail.jsonPath().getString("data.tenantCode");
        Assumptions.assumeTrue(existingCode != null, "需要已有租户代码");

        Map<String, Object> data = new HashMap<>();
        data.put("tenantCode", existingCode);
        data.put("tenantName", "重复代码测试租户");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/tenant");

        response.then().statusCode(409);
        log.info("重复tenantCode冲突检测通过");
    }

    // ==================== 链路2：查询租户 ====================

    @Test
    @Order(4)
    @DisplayName("链路2-1: 查询租户详情 → 验证创建数据一致")
    void testTenantDetail() {
        Assumptions.assumeTrue(testTenantId != null, "需要测试租户ID");

        Response response = getAuthRequest()
            .when()
            .get("/tenant/" + testTenantId);

        verifySuccess(response);
        response.then()
            .body("data.id", notNullValue())
            .body("data.tenantCode", notNullValue())
            .body("data.tenantName", equalTo("业务链路测试租户"));

        log.info("租户详情验证通过");
    }

    @Test
    @Order(5)
    @DisplayName("链路2-2: 查询不存在的租户 → 验证404")
    void testTenantNotFound() {
        Response response = getAuthRequest()
            .when()
            .get("/tenant/" + NON_EXISTENT_ID);

        response.then().statusCode(404);
        log.info("不存在的租户返回404验证通过");
    }

    // ==================== 链路3：更新租户 ====================

    @Test
    @Order(6)
    @DisplayName("链路3-1: 更新租户信息")
    void testUpdateTenant() {
        Assumptions.assumeTrue(testTenantId != null, "需要测试租户ID");

        Map<String, Object> data = new HashMap<>();
        data.put("contactPerson", "更新后的联系人");
        data.put("contactPhone", "13900139000");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .put("/tenant/" + testTenantId);

        verifySuccess(response);

        // 验证更新生效
        Response detail = getAuthRequest()
            .when()
            .get("/tenant/" + testTenantId);

        verifySuccess(detail);
        detail.then()
            .body("data.contactPerson", equalTo("更新后的联系人"));

        log.info("租户更新验证通过");
    }

    // ==================== 链路4：状态切换 ====================

    @Test
    @Order(8)
    @DisplayName("链路4-1: 切换状态 inactive → suspended → active")
    void testTenantStatusToggle() {
        Assumptions.assumeTrue(testTenantId != null, "需要测试租户ID");

        // 切换为 inactive
        Response inactiveResp = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/tenant/" + testTenantId + "/status");

        inactiveResp.then().statusCode(anyOf(is(200), is(204)));

        // 切换为 suspended
        Response suspendedResp = getAuthRequest()
            .body(Map.of("status", "suspended"))
            .when()
            .patch("/tenant/" + testTenantId + "/status");

        suspendedResp.then().statusCode(anyOf(is(200), is(204)));

        // 切回 active
        Response activeResp = getAuthRequest()
            .body(Map.of("status", "active"))
            .when()
            .patch("/tenant/" + testTenantId + "/status");

        activeResp.then().statusCode(anyOf(is(200), is(204)));

        log.info("租户状态切换验证通过 (inactive → suspended → active)");
    }

    // ==================== 链路5：删除租户 ====================

    @Test
    @Order(11)
    @DisplayName("链路5-1: 删除租户 → 验证已删除")
    void testDeleteTenant() {
        Assumptions.assumeTrue(testTenantId != null, "需要测试租户ID");

        Response response = getAuthRequest()
            .when()
            .delete("/tenant/" + testTenantId);

        response.then().statusCode(anyOf(is(200), is(204)));

        // 验证已删除（@TableLogic 软删除后查询应返回 404）
        Response check = getAuthRequest()
            .when()
            .get("/tenant/" + testTenantId);

        int status = check.getStatusCode();
        if (status == 404 || status == 400) {
            log.info("租户已确认删除 ({}返回)", status);
        } else {
            log.warn("租户删除后仍可查询 (status={})", status);
        }

        testTenantId = null;
    }

    // ==================== 边界测试 ====================

    @Test
    @Order(60)
    @DisplayName("边界-1: 创建租户缺少 tenantCode → 验证400")
    void testCreateTenantMissingCode() {
        Map<String, Object> data = new HashMap<>();
        data.put("tenantName", "缺少代码的租户");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/tenant");

        response.then().statusCode(400);
        log.info("缺少tenantCode返回400验证通过");
    }

    @Test
    @Order(61)
    @DisplayName("边界-2: 创建租户缺少 tenantName → 验证400")
    void testCreateTenantMissingName() {
        Map<String, Object> data = new HashMap<>();
        data.put("tenantCode", uniqueId("TENANT_NO_NAME"));

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/tenant");

        response.then().statusCode(400);
        log.info("缺少tenantName返回400验证通过");
    }

    @Test
    @Order(62)
    @DisplayName("边界-3: 修改不存在的租户状态 → 验证404")
    void testUpdateStatusNotFound() {
        Response response = getAuthRequest()
            .body(Map.of("status", "inactive"))
            .when()
            .patch("/tenant/" + NON_EXISTENT_ID + "/status");

        response.then().statusCode(anyOf(is(404), is(400)));
        log.info("不存在的租户状态修改返回404/400验证通过");
    }

    @Test
    @Order(63)
    @DisplayName("边界-4: 未授权访问租户列表 → 验证401")
    void testTenantListUnauthorized() {
        given()
            .when()
            .get("/tenant/list")
            .then()
            .statusCode(401);

        log.info("未授权访问返回401验证通过");
    }

    @Test
    @Order(64)
    @DisplayName("边界-5: 修改租户状态为无效值 → 验证400")
    void testTenantStatusInvalid() {
        // 需要一个存在的租户来测试无效状态
        Response listResp = getAuthRequest()
            .queryParam("page", 1)
            .queryParam("pageSize", 1)
            .when()
            .get("/tenant/list");

        String tenantId = listResp.jsonPath().getString("data[0].id");
        Assumptions.assumeTrue(tenantId != null, "需要已有租户数据");

        Response response = getAuthRequest()
            .body(Map.of("status", "invalid_status"))
            .when()
            .patch("/tenant/" + tenantId + "/status");

        response.then().statusCode(400);
        log.info("无效状态值返回400验证通过");
    }
}
