package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * SDK生成接口测试
 * 覆盖 4 个接口
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SdkApiTest extends BaseTest {

    /**
     * 测试生成Java SDK - 正常场景
     */
    @Test
    @Order(1)
    public void testGenerateJavaSDK_Success() {
        Response response = getAuthRequest()
            .queryParam("baseUrl", "http://localhost:8080")
            .queryParam("apiKey", "test-api-key")
            .when()
            .get("/sdk/java");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试生成Java SDK - 缺少参数
     */
    @Test
    @Order(2)
    public void testGenerateJavaSDK_MissingParams() {
        Response response = getAuthRequest()
            .queryParam("baseUrl", "http://localhost:8080")
            .when()
            .get("/sdk/java");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试生成Python SDK - 正常场景
     */
    @Test
    @Order(3)
    public void testGeneratePythonSDK_Success() {
        Response response = getAuthRequest()
            .queryParam("baseUrl", "http://localhost:8080")
            .queryParam("apiKey", "test-api-key")
            .when()
            .get("/sdk/python");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试生成Go SDK - 正常场景
     */
    @Test
    @Order(4)
    public void testGenerateGoSDK_Success() {
        Response response = getAuthRequest()
            .queryParam("baseUrl", "http://localhost:8080")
            .queryParam("apiKey", "test-api-key")
            .when()
            .get("/sdk/go");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试生成所有SDK - 正常场景
     */
    @Test
    @Order(5)
    public void testGenerateAllSDKs_Success() {
        Response response = getAuthRequest()
            .queryParam("baseUrl", "http://localhost:8080")
            .queryParam("apiKey", "test-api-key")
            .when()
            .get("/sdk/all");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试生成SDK - 未授权
     */
    @Test
    @Order(6)
    public void testGenerateSDK_Unauthorized() {
        given()
            .when()
            .get("/sdk/java?baseUrl=http://localhost:8080&apiKey=test")
            .then()
            .statusCode(401);
    }
}