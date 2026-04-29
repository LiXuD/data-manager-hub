package com.dataplatform.test;

import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * 数据血缘接口测试
 * 覆盖 4 个接口
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TraceApiTest extends BaseTest {

    private static Long testLineageId;

    /**
     * 测试记录血缘关系 - 正常场景
     */
    @Test
    @Order(1)
    public void testRecordLineage_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("sourceType", "TABLE");
        data.put("sourceId", 1);
        data.put("sourceName", "source_table");
        data.put("targetType", "TABLE");
        data.put("targetId", 2);
        data.put("targetName", "target_table");
        data.put("relationType", "TRANSFORM");
        data.put("transformRule", "SELECT * FROM source");

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/trace/lineage");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
            Integer id = response.jsonPath().getInt("data.id");
            if (id != null) {
                testLineageId = id.longValue();
            }
        }
    }

    /**
     * 测试记录血缘关系 - 必填参数缺失
     */
    @Test
    @Order(2)
    public void testRecordLineage_MissingRequired() {
        Map<String, Object> data = new HashMap<>();
        data.put("sourceType", "TABLE");
        data.put("sourceId", 1);
        // 缺少 targetType, targetId

        Response response = getAuthRequest()
            .body(data)
            .when()
            .post("/trace/lineage");

        response.then()
            .statusCode(anyOf(is(400), is(422)));
    }

    /**
     * 测试获取上游血缘 - 正常场景
     */
    @Test
    @Order(3)
    public void testGetUpstream_Success() {
        Response response = getAuthRequest()
            .queryParam("type", "TABLE")
            .queryParam("id", 1)
            .when()
            .get("/trace/lineage/upstream");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试获取上游血缘 - 缺少参数
     */
    @Test
    @Order(4)
    public void testGetUpstream_MissingParams() {
        Response response = getAuthRequest()
            .queryParam("type", "TABLE")
            .when()
            .get("/trace/lineage/upstream");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试获取下游血缘 - 正常场景
     */
    @Test
    @Order(5)
    public void testGetDownstream_Success() {
        Response response = getAuthRequest()
            .queryParam("type", "TABLE")
            .queryParam("id", 1)
            .when()
            .get("/trace/lineage/downstream");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试获取下游血缘 - 缺少参数
     */
    @Test
    @Order(6)
    public void testGetDownstream_MissingParams() {
        Response response = getAuthRequest()
            .queryParam("type", "TABLE")
            .when()
            .get("/trace/lineage/downstream");

        response.then()
            .statusCode(400);
    }

    /**
     * 测试获取完整血缘 - 正常场景
     */
    @Test
    @Order(7)
    public void testGetFullLineage_Success() {
        Response response = getAuthRequest()
            .queryParam("type", "TABLE")
            .queryParam("id", 1)
            .when()
            .get("/trace/lineage/full");

        if (response.getStatusCode() == 200) {
            verifySuccess(response);
        }
    }

    /**
     * 测试获取完整血缘 - 缺少参数
     */
    @Test
    @Order(8)
    public void testGetFullLineage_MissingParams() {
        Response response = getAuthRequest()
            .queryParam("type", "TABLE")
            .when()
            .get("/trace/lineage/full");

        response.then()
            .statusCode(400);
    }
}