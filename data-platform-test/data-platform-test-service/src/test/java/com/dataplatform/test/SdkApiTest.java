package com.dataplatform.test;

import com.dataplatform.sdk.generator.ApiSpec;
import com.dataplatform.sdk.generator.SDKGenerator;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SDK 生成器 Jar 测试。
 *
 * sdk 已去 Spring Boot 服务化，不再通过 Gateway 暴露 /sdk/** HTTP 接口。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SdkApiTest {

    private final SDKGenerator sdkGenerator = new SDKGenerator();

    // ======================== Backward-compatible tests ========================

    /**
     * 测试生成 Java SDK - 正常场景
     */
    @Test
    @Order(1)
    public void testGenerateJavaSDK_Success() {
        String code = sdkGenerator.generateJavaSDK("http://localhost:8888");

        assertThat(code, containsString("DataPlatformClient"));
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
        assertThat(code, containsString("X-API-Key"));
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

    // ======================== Spec-based generation tests ========================

    @Test
    @Order(10)
    public void testApiSpecFromDefaults_NonEmpty() {
        ApiSpec spec = ApiSpec.fromDefaults("http://localhost:8888");

        assertNotNull(spec);
        assertEquals("DataPlatform", spec.getServiceName());
        assertEquals("v1", spec.getVersion());
        assertFalse(spec.getEndpoints().isEmpty());
        assertTrue(spec.getEndpoints().size() >= 12, "Should have at least 12 endpoints");
    }

    @Test
    @Order(11)
    public void testGenerateJavaClient_ReturnsMultipleFiles() throws IOException {
        ApiSpec spec = ApiSpec.fromDefaults("http://localhost:8888");
        Map<String, String> files = sdkGenerator.generateJavaClient(spec);

        assertFalse(files.isEmpty());
        // Client file must exist
        assertTrue(files.containsKey("DataPlatformClient.java"));
        String client = files.get("DataPlatformClient.java");
        assertThat(client, containsString("class DataPlatformClient"));
        assertThat(client, containsString("X-API-Key"));
        // Should have real method bodies, not just stubs
        assertThat(client, containsString("listVendors"));
        assertThat(client, containsString("getVendor"));
        assertThat(client, containsString("createVendor"));
        assertThat(client, containsString("queryBilling"));
        assertThat(client, containsString("httpClient.send"));
        // Model files
        assertTrue(files.containsKey("Vendor.java"));
        assertTrue(files.containsKey("Caller.java"));
    }

    @Test
    @Order(12)
    public void testGeneratePythonClient_ReturnsMultipleFiles() throws IOException {
        ApiSpec spec = ApiSpec.fromDefaults("http://localhost:8888");
        Map<String, String> files = sdkGenerator.generatePythonClient(spec);

        assertFalse(files.isEmpty());
        assertTrue(files.containsKey("data_platform_client.py"));
        String client = files.get("data_platform_client.py");
        assertThat(client, containsString("class DataPlatformClient"));
        assertThat(client, containsString("list_vendors"));
        assertThat(client, containsString("urllib.request"));
        // Model files
        assertTrue(files.containsKey("vendor.py"));
    }

    @Test
    @Order(13)
    public void testGenerateGoClient_ReturnsMultipleFiles() throws IOException {
        ApiSpec spec = ApiSpec.fromDefaults("http://localhost:8888");
        Map<String, String> files = sdkGenerator.generateGoClient(spec);

        assertFalse(files.isEmpty());
        assertTrue(files.containsKey("client.go"));
        String client = files.get("client.go");
        assertThat(client, containsString("type Client struct"));
        assertThat(client, containsString("func NewClient"));
        assertThat(client, containsString("ListVendors"));
        assertThat(client, containsString("http.MethodGet"));
        // Model files
        assertTrue(files.containsKey("vendor.go"));
    }

    @Test
    @Order(14)
    public void testGenerateAllClients_ReturnsThreeLanguages() throws IOException {
        ApiSpec spec = ApiSpec.fromDefaults("http://localhost:8888");
        Map<String, Map<String, String>> all = sdkGenerator.generateAllClients(spec);

        assertThat(all.keySet(), containsInAnyOrder("java", "python", "go"));
        assertFalse(all.get("java").isEmpty());
        assertFalse(all.get("python").isEmpty());
        assertFalse(all.get("go").isEmpty());
    }

    @Test
    @Order(15)
    public void testGenerateJavaClient_NullSpec() {
        assertThrows(IllegalArgumentException.class, () -> sdkGenerator.generateJavaClient(null));
    }

    @Test
    @Order(16)
    public void testEndpointPathParams() {
        ApiSpec spec = ApiSpec.fromDefaults("http://localhost:8888");
        // getVendor has path param {id}
        ApiSpec.Endpoint ep = spec.getEndpoints().stream()
                .filter(e -> "getVendor".equals(e.getOperationId()))
                .findFirst().orElseThrow();

        assertTrue(ep.isHasPathParams());
        assertThat(ep.getPathParamNames(), contains("id"));
        assertFalse(ep.isHasBody());
    }

    @Test
    @Order(17)
    public void testEndpointHasBody() {
        ApiSpec spec = ApiSpec.fromDefaults("http://localhost:8888");
        ApiSpec.Endpoint ep = spec.getEndpoints().stream()
                .filter(e -> "createVendor".equals(e.getOperationId()))
                .findFirst().orElseThrow();

        assertTrue(ep.isHasBody());
        assertNotNull(ep.getBodyParam());
        assertEquals("body", ep.getBodyParam().getName());
    }
}
