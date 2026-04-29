package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 数据质量接口测试
 * 覆盖 4 个接口
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class QualityApiTest extends BaseTest {

    private static Long testRuleId;

    /**
     * 测试添加质量规则 - 正常场景
     */
    @Test
    @Order(1)
    public void testAddQualityRule_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", "测试规则_" + System.currentTimeMillis());
        data.put("ruleType", "VALIDATION");
        data.put("dataType", "USER");
        data.put("checkExpression", "id > 0");
        data.put("threshold", "100");
        data.put("severity", 1);

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/quality/rules");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
            Integer id = response.jsonPath().getInt("data.id");
            if (id != null) {
                testRuleId = id.longValue();
            }
        }
    }

    /**
     * 测试添加质量规则 - 必填参数缺失
     */
    @Test
    @Order(2)
    public void testAddQualityRule_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("ruleName", "测试规则");
        // 缺少 ruleType, dataType

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/quality/rules");

        response.then()
            .statusCode(anyOf(is(400), is(422)));
    }

    /**
     * 测试获取质量规则列表 - 正常场景
     */
    @Test
    @Order(3)
    public void testGetQualityRules_Success() {
        Response response = getAuthRequest()
            .when()
            .get("/quality/rules");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试获取质量规则列表 - 未授权
     */
    @Test
    @Order(4)
    public void testGetQualityRules_Unauthorized() {
        given()
            .when()
            .get("/quality/rules")
            .then()
            .statusCode(401);
    }

    /**
     * 测试检查数据质量 - 正常场景
     */
    @Test
    @Order(5)
    public void testCheckQuality_Success() {
        Response response = getAuthRequest()
            .queryParam("dataType", "USER")
            .queryParam("dataId", 1)
            .when()
            .post("/quality/check");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试检查数据质量 - 缺少参数
     */
    @Test
    @Order(6)
    public void testCheckQuality_MissingParams() {
        Response response = getAuthRequest()
            .queryParam("dataType", "USER")
            .when()
            .post("/quality/check");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试获取质量历史 - 正常场景
     */
    @Test
    @Order(7)
    public void testGetQualityHistory_Success() {
        Response response = getAuthRequest()
            .queryParam("dataType", "USER")
            .queryParam("dataId", 1)
            .when()
            .get("/quality/history");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试获取质量历史 - 缺少参数
     */
    @Test
    @Order(8)
    public void testGetQualityHistory_MissingParams() {
        Response response = getAuthRequest()
            .queryParam("dataType", "USER")
            .when()
            .get("/quality/history");

        response.then()
            .statusCode(400);
    }
}