package com.dataplatform.test;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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
@Tag("integration")
public class BaseTest {

    private static final Logger log = LoggerFactory.getLogger(BaseTest.class);

    /** Gateway 统一入口 */
    protected static final String GATEWAY_URL = configOrDefault("test.gateway-url", "GATEWAY_URL", "http://localhost:8888");

    /** ID used to test "not found" scenarios */
    protected static final Long NON_EXISTENT_ID = 999999999L;

    protected String authToken;
    protected static Long testTenantId;

    /** 清理任务列表，子类通过 registerDeleteById 注册，@AfterAll 逆序执行 */
    protected static final List<Runnable> cleanupTasks = new ArrayList<>();

    /** 注册按 ID 删除的清理任务 */
    protected void registerDeleteById(String urlTemplate, Long id) {
        cleanupTasks.add(() -> {
            Response response = given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + authToken)
                .when()
                .delete(GATEWAY_URL + "/api/v1" + urlTemplate, id);
            int status = response.statusCode();
            if (status != 200 && status != 204 && status != 404) {
                throw new AssertionError("清理测试资源失败: " + urlTemplate + ", id=" + id + ", status=" + status);
            }
        });
    }

    @AfterAll
    static void cleanupAll() {
        List<Runnable> reversed = new ArrayList<>(cleanupTasks);
        Collections.reverse(reversed);
        List<Throwable> failures = new ArrayList<>();
        for (Runnable task : reversed) {
            try {
                task.run();
            } catch (Throwable failure) {
                failures.add(failure);
            }
        }
        cleanupTasks.clear();
        org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(),
                () -> "测试资源清理失败: " + failures.stream().map(Throwable::getMessage).toList());
    }

    static {
        // 通过 Gateway 统一入口测试
        RestAssured.baseURI = GATEWAY_URL;
        RestAssured.basePath = "/api/v1";
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeEach
    public void setUp() {
        login();
    }

    /**
     * 登录获取 token
     */
    protected void login() {
        Map<String, String> loginData = new HashMap<>();
        loginData.put("username", requiredConfig("test.username", "TEST_USERNAME"));
        loginData.put("password", requiredConfig("test.password", "TEST_PASSWORD"));

        Response response = given()
            .contentType("application/json")
            .body(loginData)
            .when()
            .post("/auth/login");

        response.then().statusCode(200).body("code", equalTo(200));
        authToken = response.jsonPath().getString("data.token");
        org.junit.jupiter.api.Assertions.assertNotNull(authToken, "登录响应缺少token");
        log.info("登录成功, 获取 token: ***{}", authToken.substring(Math.max(0, authToken.length() - 4)));
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
            .body("code", equalTo(200));
        String message = response.jsonPath().getString("msg");
        if (message == null) {
            message = response.jsonPath().getString("message");
        }
        org.junit.jupiter.api.Assertions.assertTrue(message != null && !message.isBlank(),
                "成功响应必须包含非空的msg或message");
    }

    /**
     * 验证失败响应
     */
    protected void verifyError(Response response, int expectedStatus) {
        response.then()
            .statusCode(expectedStatus)
            .body("code", not(equalTo(200)));
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

    /** 前置资源创建失败时立即让测试失败。 */
    protected void skipIfNull(Long id, String entityName) {
        org.junit.jupiter.api.Assertions.assertNotNull(id, "No test " + entityName + " available");
    }

    /** 生成唯一标识符 (名称/代码) */
    protected String uniqueId(String prefix) {
        return prefix + "_" + System.currentTimeMillis();
    }

    private static String requiredConfig(String property, String environment) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required integration-test setting: " + environment);
        }
        return value;
    }

    private static String configOrDefault(String property, String environment, String defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
