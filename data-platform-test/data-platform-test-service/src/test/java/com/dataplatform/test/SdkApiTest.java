package com.dataplatform.test;

import com.dataplatform.sdk.generator.SDKGenerator;
import org.junit.jupiter.api.*;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SDK 生成器 Jar 测试。
 *
 * sdk 已去 Spring Boot 服务化，不再通过 Gateway 暴露 /sdk/** HTTP 接口。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SdkApiTest {

    private final SDKGenerator sdkGenerator = new SDKGenerator();

    /**
     * 测试生成 Java SDK - 正常场景
     */
    @Test
    @Order(1)
    public void testGenerateJavaSDK_Success() {
        String code = sdkGenerator.generateJavaSDK("http://localhost:8888");

        assertThat(code, containsString("class DataPlatformClient"));
        assertThat(code, containsString("X-API-Key"));
    }

    /**
     * 测试生成 SDK - 缺少 baseUrl
     */
    @Test
    @Order(2)
    public void testGenerateSDK_MissingBaseUrl() {
        assertThrows(IllegalArgumentException.class, () -> sdkGenerator.generateJavaSDK(" "));
    }

    /**
     * 测试生成 Python SDK - 正常场景
     */
    @Test
    @Order(3)
    public void testGeneratePythonSDK_Success() {
        String code = sdkGenerator.generatePythonSDK("http://localhost:8888");

        assertThat(code, containsString("class DataPlatformClient"));
        assertThat(code, containsString("requests.Session"));
    }

    /**
     * 测试生成 Go SDK - 正常场景
     */
    @Test
    @Order(4)
    public void testGenerateGoSDK_Success() {
        String code = sdkGenerator.generateGoSDK("http://localhost:8888");

        assertThat(code, containsString("type Client struct"));
        assertThat(code, containsString("func NewClient"));
    }

    /**
     * 测试生成所有 SDK - 正常场景
     */
    @Test
    @Order(5)
    public void testGenerateAllSDKs_Success() {
        assertThat(sdkGenerator.generateAllSDKs("http://localhost:8888").keySet(),
                containsInAnyOrder("java", "python", "go"));
    }
}
