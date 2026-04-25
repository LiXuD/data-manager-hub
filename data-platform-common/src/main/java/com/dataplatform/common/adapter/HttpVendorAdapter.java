package com.dataplatform.common.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
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
    private final ObjectMapper objectMapper;

    public HttpVendorAdapter(String vendorCode) {
        this.vendorCode = vendorCode;
        this.objectMapper = new ObjectMapper();
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

        try {
            // 1. 转换请求参数
            Map<String, Object> vendorParams = transformRequest(params, config.getRequestTemplate());

            // 2. 构建请求
            Request request = buildRequest(config, vendorParams);

            // 3. 执行请求
            try (Response response = httpClient.newCall(request).execute()) {
                long latency = System.currentTimeMillis() - startTime;

                // 4. 处理响应
                return handleResponse(response, config, latency);
            }

        } catch (IOException e) {
            log.error("厂商API调用失败: vendor={}, url={}, error={}",
                vendorCode, config.getApiUrl(), e.getMessage());

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("errorCode", "VENDOR_ERROR");
            errorResult.put("errorMsg", e.getMessage());
            errorResult.put("latency", System.currentTimeMillis() - startTime);
            return errorResult;
        }
    }

    /**
     * 构建 HTTP 请求
     */
    private Request buildRequest(VendorAdapterConfig config, Map<String, Object> params) throws IOException {
        String url = config.getApiUrl();
        String method = config.getMethod() != null ? config.getMethod().toUpperCase() : "POST";

        // 构建请求体
        String jsonBody = objectMapper.writeValueAsString(params);
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

        // 设置请求方法
        if ("GET".equals(method)) {
            HttpUrl httpUrl = HttpUrl.parse(url);
            if (httpUrl == null) {
                throw new IllegalArgumentException("Invalid URL: " + url);
            }
            HttpUrl.Builder urlBuilder = httpUrl.newBuilder();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                urlBuilder.addQueryParameter(entry.getKey(), String.valueOf(entry.getValue()));
            }
            builder.url(urlBuilder.build()).get();
        } else {
            builder.post(body);
        }

        return builder.build();
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
}
