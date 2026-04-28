package com.dataplatform.test;

import com.dataplatform.common.security.SignatureBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 签名构建器测试
 */
@DisplayName("签名构建器测试")
class SignatureBuilderTest {

    @Test
    @DisplayName("HMAC-SHA256签名 - 正常场景")
    void testHmacSha256_Success() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");
        params.put("idCard", "110101199001011234");
        params.put("timestamp", "1640000000000");

        String secretKey = "test_secret_key_123";
        String sign = SignatureBuilder.hmacSha256(params, secretKey);

        assertNotNull(sign);
        assertEquals(64, sign.length());
        assertTrue(sign.matches("[0-9a-f]+"));
    }

    @Test
    @DisplayName("HMAC-SHA256签名 - 参数排序一致性")
    void testHmacSha256_ParameterOrder() {
        Map<String, Object> params1 = new HashMap<>();
        params1.put("b", "2");
        params1.put("a", "1");
        params1.put("c", "3");

        Map<String, Object> params2 = new HashMap<>();
        params2.put("c", "3");
        params2.put("a", "1");
        params2.put("b", "2");

        String secretKey = "test_key";
        String sign1 = SignatureBuilder.hmacSha256(params1, secretKey);
        String sign2 = SignatureBuilder.hmacSha256(params2, secretKey);

        assertEquals(sign1, sign2, "相同参数不同顺序应产生相同签名");
    }

    @Test
    @DisplayName("HMAC-SHA256签名 - 空参数")
    void testHmacSha256_EmptyParams() {
        Map<String, Object> params = new HashMap<>();
        String secretKey = "test_key";

        String sign = SignatureBuilder.hmacSha256(params, secretKey);

        assertNotNull(sign);
        assertEquals(64, sign.length());
    }

    @Test
    @DisplayName("HMAC-SHA256签名 - 空值过滤")
    void testHmacSha256_NullValueFilter() {
        Map<String, Object> params = new HashMap<>();
        params.put("a", "1");
        params.put("b", null);
        params.put("c", "");

        String secretKey = "test_key";
        String sign = SignatureBuilder.hmacSha256(params, secretKey);

        assertNotNull(sign);

        Map<String, Object> filteredParams = new HashMap<>();
        filteredParams.put("a", "1");
        String expectedSign = SignatureBuilder.hmacSha256(filteredParams, secretKey);

        assertEquals(expectedSign, sign, "空值应被过滤");
    }

    @Test
    @DisplayName("MD5签名 - 正常场景")
    void testMd5_Success() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");
        params.put("mobile", "13800138000");

        String secretKey = "md5_secret";
        String sign = SignatureBuilder.md5(params, secretKey);

        assertNotNull(sign);
        assertEquals(32, sign.length());
        assertTrue(sign.matches("[0-9a-f]+"));
    }

    @Test
    @DisplayName("MD5签名 - 可重现性")
    void testMd5_Reproducible() {
        Map<String, Object> params = new HashMap<>();
        params.put("key", "value");

        String secretKey = "secret";

        String sign1 = SignatureBuilder.md5(params, secretKey);
        String sign2 = SignatureBuilder.md5(params, secretKey);

        assertEquals(sign1, sign2, "相同输入应产生相同签名");
    }

    @Test
    @DisplayName("签名类型选择 - HMAC_SHA256")
    void testSign_HmacSha256() {
        Map<String, Object> params = new HashMap<>();
        params.put("data", "test");

        String sign = SignatureBuilder.sign(params, "secret", "HMAC_SHA256");

        assertNotNull(sign);
        assertEquals(64, sign.length());
    }

    @Test
    @DisplayName("签名类型选择 - HMAC-SHA256")
    void testSign_HmacSha256WithDash() {
        Map<String, Object> params = new HashMap<>();
        params.put("data", "test");

        String sign1 = SignatureBuilder.sign(params, "secret", "HMAC_SHA256");
        String sign2 = SignatureBuilder.sign(params, "secret", "HMAC-SHA256");

        assertEquals(sign1, sign2, "HMAC_SHA256和HMAC-SHA256应等效");
    }

    @Test
    @DisplayName("签名类型选择 - MD5")
    void testSign_Md5() {
        Map<String, Object> params = new HashMap<>();
        params.put("data", "test");

        String sign = SignatureBuilder.sign(params, "secret", "MD5");

        assertNotNull(sign);
        assertEquals(32, sign.length());
    }

    @Test
    @DisplayName("签名类型选择 - 不支持的类型")
    void testSign_UnsupportedType() {
        Map<String, Object> params = new HashMap<>();
        params.put("data", "test");

        assertThrows(IllegalArgumentException.class, () ->
            SignatureBuilder.sign(params, "secret", "RSA"));
    }

    @Test
    @DisplayName("签名类型选择 - 空类型")
    void testSign_NullType() {
        Map<String, Object> params = new HashMap<>();
        params.put("data", "test");

        String sign = SignatureBuilder.sign(params, "secret", null);

        assertNull(sign);
    }

    @Test
    @DisplayName("签名类型选择 - 空字符串类型")
    void testSign_EmptyType() {
        Map<String, Object> params = new HashMap<>();
        params.put("data", "test");

        String sign = SignatureBuilder.sign(params, "secret", "");

        assertNull(sign);
    }
}
