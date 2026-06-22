package com.dataplatform.common.adapter;

import com.dataplatform.common.auth.AuthHandler;
import com.dataplatform.common.auth.AuthHandlerFactory;
import com.dataplatform.common.security.SignatureBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP厂商适配器
 * 通过HTTP/HTTPS调用厂商API
 */
public class HttpVendorAdapter extends AbstractVendorAdapter {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final String vendorCode;
    private final OkHttpClient httpClient;
    private final AuthHandlerFactory authHandlerFactory;

    public HttpVendorAdapter(String vendorCode) {
        this(vendorCode, new AuthHandlerFactory());
    }

    public HttpVendorAdapter(String vendorCode, AuthHandlerFactory authHandlerFactory) {
        this.vendorCode = vendorCode;
        this.authHandlerFactory = authHandlerFactory;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String getVendorCode() {
        return vendorCode;
    }

    @Override
    public boolean supports(String dataTypeCode) {
        // 默认支持所有数据类型
        return true;
    }

    @Override
    public Map<String, Object> execute(VendorAdapterConfig config, Map<String, Object> params) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }

        long startTime = System.currentTimeMillis();
        String url = config.getApiUrl();
        String method = config.getMethod() != null ? config.getMethod().toUpperCase() : "POST";

        Map<String, Object> vendorParams = transformRequest(params, config.getRequestTemplate());
        log.info("[VENDOR-REQ] {} {} | vendor={} | params={}", method, url, vendorCode, truncateJson(vendorParams));

        try {
            Request request = buildRequest(config, vendorParams);

            try (Response response = httpClient.newCall(request).execute()) {
                long latency = System.currentTimeMillis() - startTime;
                Map<String, Object> result = handleResponse(response, config, latency);

                boolean success = Boolean.TRUE.equals(result.get("success"));
                String rawResponse = result.containsKey("rawResponse")
                    ? truncateJson(result.get("rawResponse")) : "[no body]";

                log.info("[VENDOR-RES] {} {} | vendor={} | status={} | success={} | {}ms | response={}",
                        method, url, vendorCode, response.code(), success, latency, rawResponse);

                return result;
            }

        } catch (IOException e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("[VENDOR-ERR] {} {} | vendor={} | {}ms | error={}",
                    method, url, vendorCode, latency, e.getMessage());

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("errorCode", "VENDOR_ERROR");
            errorResult.put("errorMsg", e.getMessage());
            errorResult.put("latency", latency);
            return errorResult;
        }
    }

    /**
     * 构建 HTTP 请求
     */
    private Request buildRequest(VendorAdapterConfig config, Map<String, Object> params) throws IOException {
        String url = config.getApiUrl();
        String method = config.getMethod() != null ? config.getMethod().toUpperCase() : "POST";

        // 添加签名
        Map<String, Object> signedParams = addSignature(config, params);

        // 构建请求体
        String jsonBody = objectMapper.writeValueAsString(signedParams);
        RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);

        // 构建请求构建器
        Request.Builder builder = new Request.Builder().url(url);

        // 添加请求头
        if (config.getHeaders() != null) {
            for (Map.Entry<String, String> header : config.getHeaders().entrySet()) {
                String value = header.getValue();
                // 支持变量替换: {secretKey} -> 实际密钥
                if (value.contains("{secretKey}") && config.getSecretKey() != null) {
                    value = value.replace("{secretKey}", config.getSecretKey());
                }
                builder.addHeader(header.getKey(), value);
            }
        }

        // 应用认证配置
        applyAuth(builder, config);

        // 设置请求方法
        if ("GET".equals(method)) {
            HttpUrl httpUrl = HttpUrl.parse(url);
            if (httpUrl == null) {
                throw new IllegalArgumentException("Invalid URL: " + url);
            }
            HttpUrl.Builder urlBuilder = httpUrl.newBuilder();
            for (Map.Entry<String, Object> entry : signedParams.entrySet()) {
                urlBuilder.addQueryParameter(entry.getKey(), String.valueOf(entry.getValue()));
            }
            builder.url(urlBuilder.build()).get();
        } else {
            builder.post(body);
        }

        return builder.build();
    }

    /**
     * 应用认证配置
     */
    private void applyAuth(Request.Builder builder, VendorAdapterConfig config) {
        String authType = config.getAuthType();
        Map<String, Object> authConfig = config.getAuthConfig();

        if (!StringUtils.hasText(authType) || authConfig == null) {
            return;
        }

        AuthHandler handler = authHandlerFactory.getHandler(authType);
        if (handler != null) {
            handler.applyAuth(builder, authConfig, Collections.singletonMap("vendorCode", vendorCode));
            log.debug("应用认证: type={}", authType);
        }
    }

    /**
     * 添加签名
     */
    private Map<String, Object> addSignature(VendorAdapterConfig config, Map<String, Object> params) {
        if (StringUtils.hasText(config.getSignType()) && StringUtils.hasText(config.getSecretKey())) {
            Map<String, Object> signedParams = new HashMap<>(params);
            String sign = SignatureBuilder.sign(params, config.getSecretKey(), config.getSignType());
            signedParams.put("sign", sign);
            log.debug("生成签名: type={}", config.getSignType());
            return signedParams;
        }
        return params;
    }

    /**
     * 处理 HTTP 响应
     */
    private Map<String, Object> handleResponse(Response response, VendorAdapterConfig config, long latency)
            throws IOException {

        Map<String, Object> result = new HashMap<>();
        result.put("latency", latency);

        if (!response.isSuccessful()) {
            result.put("success", false);
            result.put("errorCode", "HTTP_" + response.code());
            result.put("errorMsg", "HTTP请求失败: " + response.code());
            return result;
        }

        // 解析响应体
        ResponseBody responseBody = response.body();
        if (responseBody == null) {
            result.put("success", false);
            result.put("errorCode", "EMPTY_RESPONSE");
            result.put("errorMsg", "响应体为空");
            return result;
        }

        String responseStr = responseBody.string();
        Map<String, Object> vendorResponse = objectMapper.readValue(responseStr,
            new TypeReference<Map<String, Object>>() {});

        // 转换响应字段
        Map<String, Object> transformedResponse = transformResponse(vendorResponse, config.getResponseMapping());

        result.put("success", true);
        result.put("data", transformedResponse);
        result.put("rawResponse", vendorResponse);

        return result;
    }

    private String truncateJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            String json = objectMapper.writeValueAsString(obj);
            return json.length() <= 2048 ? json : json.substring(0, 2048) + "...[truncated]";
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
