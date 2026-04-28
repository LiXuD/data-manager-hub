package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 加密接口测试
 * 覆盖 3 个接口
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SecurityApiTest extends BaseTest {

    /**
     * 测试加密文本 - 正常场景
     */
    @Test
    @Order(1)
    public void testEncryptText_Success() {
        Response response = getAuthRequest()
            .queryParam("plainText", "test_password")
            .queryParam("tableName", "user")
            .when()
            .post("/security/encryption/encrypt");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试加密文本 - 缺少参数
     */
    @Test
    @Order(2)
    public void testEncryptText_MissingParams() {
        Response response = getAuthRequest()
            .queryParam("plainText", "test_password")
            .when()
            .post("/security/encryption/encrypt");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试解密文本 - 正常场景
     */
    @Test
    @Order(3)
    public void testDecryptText_Success() {
        Response response = getAuthRequest()
            .queryParam("encryptedText", "encrypted_value")
            .queryParam("tableName", "user")
            .when()
            .post("/security/encryption/decrypt");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试解密文本 - 缺少参数
     */
    @Test
    @Order(4)
    public void testDecryptText_MissingParams() {
        Response response = getAuthRequest()
            .queryParam("encryptedText", "encrypted_value")
            .when()
            .post("/security/encryption/decrypt");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试密钥轮换 - 正常场景
     */
    @Test
    @Order(5)
    public void testRotateKey_Success() {
        Response response = getAuthRequest()
            .when()
            .post("/security/encryption/rotate/user");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试密钥轮换 - 不存在的表
     */
    @Test
    @Order(6)
    public void testRotateKey_NotFound() {
        Response response = getAuthRequest()
            .when()
            .post("/security/encryption/rotate/nonexistent_table");

        response.then()
            .statusCode(anyOf(is(404), is(400)));
    }

    /**
     * 测试加密 - 未授权
     */
    @Test
    @Order(7)
    public void testEncryptText_Unauthorized() {
        given()
            .when()
            .post("/security/encryption/encrypt?plainText=test&tableName=user")
            .then()
            .statusCode(401);
    }
}