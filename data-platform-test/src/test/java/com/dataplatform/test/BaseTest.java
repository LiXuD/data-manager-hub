package com.dataplatform.test;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * API 测试基类
 */
public class BaseTest {

    protected static final String BASE_URL = "http://localhost:8080";
    protected static final String DB_URL = "jdbc:postgresql://localhost:5432/dataplatform";
    protected static final String DB_USER = "postgres";
    protected static final String DB_PASSWORD = "123456";

    protected String authToken;
    protected Long testTenantId;
    protected Long testUserId;

    static {
        RestAssured.baseURI = BASE_URL;
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
        }
    }

    /**
     * 获取带认证的请求规范
     */
    protected RequestSpecification getAuthRequest() {
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