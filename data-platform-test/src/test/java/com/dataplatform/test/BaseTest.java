package com.dataplatform.test;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * API 测试基类
 */
public class BaseTest {

    private static final Logger log = LoggerFactory.getLogger(BaseTest.class);

    protected static final String BASE_URL = "http://localhost:8888";

    // 各服务端口映射
    protected static final String USER_URL = "http://localhost:8087";
    protected static final String TENANT_URL = "http://localhost:8086";
    protected static final String VENDOR_URL = "http://localhost:8081";
    protected static final String ROLE_URL = "http://localhost:8088";
    protected static final String BILLING_URL = "http://localhost:8083";
    protected static final String CALL_URL = "http://localhost:8084";
    protected static final String CALLER_URL = "http://localhost:8082";
    protected static final String MONITOR_URL = "http://localhost:8085";

    protected static final String DB_URL = "jdbc:postgresql://localhost:5432/dataplatform";
    protected static final String DB_USER = "postgres";
    protected static final String DB_PASSWORD = "123456";

    protected String authToken;
    protected static Long testTenantId;
    protected static Long testUserId;

    static {
        RestAssured.baseURI = BASE_URL;
        RestAssured.basePath = "/api/v1";
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeEach
    public void setUp() {
        // 登录获取 token
        login();
    }

    /**
     * 登录获取 token
     */
    protected void login() {
        Map<String, String> loginData = new HashMap<>();
        loginData.put("username", "admin");
        loginData.put("password", "admin123");

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
}