package com.dataplatform.test;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * API 测试基类
 *
 * 所有测试通过 Gateway (8888) 统一入口进行，模拟前端请求
 *
 * 服务端口映射（模块合并后）：
 * - Gateway: 8888
 * - masterdata: 8081 (vendor, config, datatype, interface, graylog)
 * - access: 8082 (caller, api-key, call-record, openapi)
 * - billing-service: 8084
 * - governance: 8085 (alert, log, quality, trace)
 * - identity: 8086 (tenant, auth, user, role, security)
 */
public class BaseTest {

    private static final Logger log = LoggerFactory.getLogger(BaseTest.class);

    /** Gateway 统一入口 */
    protected static final String GATEWAY_URL = "http://localhost:8888";

    /** ID used to test "not found" scenarios */
    protected static final Long NON_EXISTENT_ID = 999999999L;

    // 各服务直连端口（用于调试）
    protected static final String VENDOR_URL = "http://localhost:8081";
    protected static final String CALLER_URL = "http://localhost:8082";
    protected static final String BILLING_URL = "http://localhost:8084";
    protected static final String GOVERNANCE_URL = "http://localhost:8085";
    protected static final String TENANT_URL = "http://localhost:8086";

    protected static final String DB_URL = "jdbc:postgresql://localhost:5432/dataplatform";
    protected static final String DB_USER = "postgres";
    protected static final String DB_PASSWORD = "123456";

    protected String authToken;
    protected static Long testTenantId;

    /** 清理任务列表，子类通过 registerDeleteById 注册，@AfterAll 逆序执行 */
    protected static final List<Runnable> cleanupTasks = new ArrayList<>();

    /** 注册按 ID 删除的清理任务 */
    protected void registerDeleteById(String urlTemplate, Long id) {
        cleanupTasks.add(() -> {
            try {
                given()
                    .contentType("application/json")
                    .header("Authorization", "Bearer " + authToken)
                    .when()
                    .delete(GATEWAY_URL + "/api/v1" + urlTemplate, id);
            } catch (Exception ignored) {
            }
        });
    }

    @AfterAll
    static void cleanupAll() {
        List<Runnable> reversed = new ArrayList<>(cleanupTasks);
        Collections.reverse(reversed);
        reversed.forEach(Runnable::run);
        cleanupTasks.clear();
    }

    static {
        // 通过 Gateway 统一入口测试
        RestAssured.baseURI = GATEWAY_URL;
        RestAssured.basePath = "/api/v1";
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeEach
    public void setUp() {
        Assumptions.assumeTrue(
            Boolean.parseBoolean(System.getProperty(
                "integration.tests",
                System.getenv().getOrDefault("INTEGRATION_TESTS", "false"))),
            "External API integration tests are disabled by default. Set -Dintegration.tests=true or INTEGRATION_TESTS=true to run them."
        );
        login();
    }

    /**
     * 登录获取 token
     */
    protected void login() {
        Map<String, String> loginData = new HashMap<>();
        loginData.put("username", "admin");
        loginData.put("password", "Test123456");

        Response response = given()
            .contentType("application/json")
            .body(loginData)
            .when()
            .post("/auth/login");

        if (response.getStatusCode() == 200) {
            authToken = response.jsonPath().getString("data.token");
            log.info("登录成功, 获取 token: {}", authToken != null ? "***" + authToken.substring(Math.max(0, authToken.length() - 4)) : "null");
        } else {
            log.error("登录失败, 状态码: {}, 响应: {}", response.getStatusCode(), response.getBody().asString());
            throw new RuntimeException("登录失败, 状态码: " + response.getStatusCode());
        }
    }

    /**
     * 获取带认证的请求规范
     */
    protected RequestSpecification getAuthRequest() {
        if (authToken == null) {
            log.error("authToken 为 null, 无法创建认证请求");
            throw new IllegalStateException("authToken 为 null, 请先调用 login() 方法");
        }
        return given()
            .contentType("application/json")
            .header("Authorization", "Bearer " + authToken);
    }

    /**
     * 验证成功响应
     */
    protected void verifySuccess(Response response) {
        response.then()
            .statusCode(200)
            .body("code", equalTo(0))
            .body("message", notNullValue());
    }

    /**
     * 验证失败响应
     */
    protected void verifyError(Response response, int expectedStatus) {
        response.then()
            .statusCode(expectedStatus)
            .body("code", not(equalTo(0)));
    }

    /** 验证响应为 404 或 400 (资源不存在或请求错误) */
    protected void verifyNotFoundOrBadRequest(Response response) {
        response.then().statusCode(anyOf(is(404), is(400)));
    }

    /** 验证响应为 200 或 204 (成功或无内容) */
    protected void verifySuccessOrNoContent(Response response) {
        response.then().statusCode(anyOf(is(200), is(204)));
    }

    /** 验证响应为 400 或 409 (参数错误或冲突) */
    protected void verifyConflictOrBadRequest(Response response) {
        response.then().statusCode(anyOf(is(400), is(409)));
    }

    /** 从响应中提取 data.id */
    protected Long extractId(Response response) {
        Integer id = response.jsonPath().getInt("data.id");
        return id != null ? id.longValue() : null;
    }

    /** 跳过测试如果 ID 为 null */
    protected void skipIfNull(Long id, String entityName) {
        if (id == null) {
            Assumptions.assumeTrue(false, "No test " + entityName + " available");
        }
    }

    /** 生成唯一标识符 (名称/代码) */
    protected String uniqueId(String prefix) {
        return prefix + "_" + System.currentTimeMillis();
    }
}
