package com.dataplatform.common.adapter;

import com.dataplatform.common.auth.AuthHandler;
import com.dataplatform.common.auth.AuthHandlerFactory;
import com.dataplatform.common.security.SignatureBuilder;
import com.dataplatform.common.util.LogTruncationUtil;
import com.fasterxml.jackson.core.type.TypeReference;
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
        if (log.isInfoEnabled()) {
            log.info("[VENDOR-REQ] {} {} | vendor={} | params={}", method, url, vendorCode,
                    LogTruncationUtil.truncate(vendorParams, LogTruncationUtil.SHORT));
        }

        try {
            Request request = buildRequest(config, vendorParams, method);

            try (Response response = httpClient.newCall(request).execute()) {
                long latency = System.currentTimeMillis() - startTime;
                VendorCallResult callResult = handleResponse(response, config, latency);

                if (log.isInfoEnabled()) {
                    log.info("[VENDOR-RES] {} {} | vendor={} | status={} | success={} | {}ms | response={}",
                            method, url, vendorCode, response.code(), callResult.success, latency,
                            LogTruncationUtil.truncate(callResult.rawResponse, LogTruncationUtil.SHORT));
                }

                return callResult.toMap();
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

    private Request buildRequest(VendorAdapterConfig config, Map<String, Object> params, String method) throws IOException {
        String url = config.getApiUrl();

        Map<String, Object> signedParams = addSignature(config, params);
        String jsonBody = objectMapper.writeValueAsString(signedParams);
        RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);

        Request.Builder builder = new Request.Builder().url(url);

        if (config.getHeaders() != null) {
            for (Map.Entry<String, String> header : config.getHeaders().entrySet()) {
                String value = header.getValue();
                if (value.contains("{secretKey}") && config.getSecretKey() != null) {
                    value = value.replace("{secretKey}", config.getSecretKey());
                }
                builder.addHeader(header.getKey(), value);
            }
        }

        applyAuth(builder, config);

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
        } else {
            log.warn("未知的认证类型: vendor={}, authType={}", vendorCode, authType);
        }
    }

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

    private VendorCallResult handleResponse(Response response, VendorAdapterConfig config, long latency)
            throws IOException {

        ResponseBody responseBody = response.body();
        String rawBody = (responseBody != null) ? responseBody.string() : "";

        if (!response.isSuccessful()) {
            String errorDetail = rawBody.isEmpty()
                ? "HTTP请求失败: " + response.code()
                : "HTTP请求失败: " + response.code() + " | " + LogTruncationUtil.truncate(rawBody, LogTruncationUtil.SHORT);
            return new VendorCallResult(false, null, rawBody,
                    "HTTP_" + response.code(), errorDetail, latency);
        }

        if (rawBody.isEmpty()) {
            return new VendorCallResult(false, null, null,
                    "EMPTY_RESPONSE", "响应体为空", latency);
        }

        String logResponse = LogTruncationUtil.truncate(rawBody, LogTruncationUtil.SHORT);
        Map<String, Object> vendorResponse = objectMapper.readValue(rawBody,
            new TypeReference<Map<String, Object>>() {});

        Map<String, Object> transformedResponse = transformResponse(vendorResponse, config.getResponseMapping());

        return new VendorCallResult(true, transformedResponse, logResponse, null, null, latency);
    }

    private record VendorCallResult(boolean success, Map<String, Object> data, String rawResponse,
                                     String errorCode, String errorMsg, long latency) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("success", success);
            map.put("latency", latency);
            if (success) {
                map.put("data", data);
                map.put("rawResponse", rawResponse);
            } else {
                map.put("errorCode", errorCode);
                map.put("errorMsg", errorMsg);
            }
            return map;
        }
    }
}
