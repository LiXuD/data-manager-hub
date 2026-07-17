package com.dataplatform.common.adapter;

import com.dataplatform.common.auth.AuthHandler;
import com.dataplatform.common.auth.AuthHandlerFactory;
import com.dataplatform.common.security.SignatureBuilder;
import com.dataplatform.common.security.pipeline.SecurityDirection;
import com.dataplatform.common.security.pipeline.SecurityExecutionContext;
import com.dataplatform.common.security.pipeline.SecurityPipelineExecutor;
import com.dataplatform.common.util.LogTruncationUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.*;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
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
    private final SecurityPipelineExecutor securityPipelineExecutor = new SecurityPipelineExecutor();

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
        SecurityExecutionContext requestContext = new SecurityExecutionContext(
                SecurityDirection.REQUEST,
                vendorParams,
                config.getHeaders(),
                Map.of(),
                config.getResolvedSecrets());
        if (hasEnabledSecuritySteps(config, SecurityDirection.REQUEST)) {
            securityPipelineExecutor.execute(SecurityDirection.REQUEST, config.getSecuritySteps(), requestContext);
        } else {
            requestContext.getParams().clear();
            requestContext.getParams().putAll(addSignature(config, vendorParams));
        }
        if (log.isInfoEnabled()) {
            log.info("[VENDOR-REQ] {} {} | vendor={} | params={}", method, url, vendorCode,
                    LogTruncationUtil.truncate(vendorParams, LogTruncationUtil.SHORT));
        }

        try {
            Request request = buildRequest(config, requestContext, method);

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

    private Request buildRequest(VendorAdapterConfig config, SecurityExecutionContext context, String method) throws IOException {
        String url = config.getApiUrl();

        Map<String, Object> requestParams = context.getParams();
        String jsonBody = context.getBody() != null ? context.getBody() : objectMapper.writeValueAsString(requestParams);
        RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);

        Request.Builder builder = new Request.Builder().url(url);

        if (context.getHeaders() != null) {
            for (Map.Entry<String, String> header : context.getHeaders().entrySet()) {
                String value = header.getValue();
                if (value != null && value.contains("{secretKey}") && config.getSecretKey() != null) {
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
            for (Map.Entry<String, Object> entry : requestParams.entrySet()) {
                urlBuilder.addQueryParameter(entry.getKey(), String.valueOf(entry.getValue()));
            }
            context.getQuery().forEach(urlBuilder::addQueryParameter);
            builder.url(urlBuilder.build()).get();
        } else {
            if (!context.getQuery().isEmpty()) {
                HttpUrl httpUrl = HttpUrl.parse(url);
                if (httpUrl == null) {
                    throw new IllegalArgumentException("Invalid URL: " + url);
                }
                HttpUrl.Builder urlBuilder = httpUrl.newBuilder();
                context.getQuery().forEach(urlBuilder::addQueryParameter);
                builder.url(urlBuilder.build());
            }
            switch (method) {
                case "POST" -> builder.post(body);
                case "PUT" -> builder.put(body);
                case "PATCH" -> builder.patch(body);
                case "DELETE" -> builder.delete(body);
                default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            }
        }

        return builder.build();
    }

    private void applyAuth(Request.Builder builder, VendorAdapterConfig config) {
        String authType = config.getAuthType();
        Map<String, Object> authConfig = config.getAuthConfig();

        if (!StringUtils.hasText(authType) || "NONE".equalsIgnoreCase(authType.trim())) {
            return;
        }
        if (authConfig == null) {
            throw new IllegalArgumentException("认证类型已配置但认证参数为空: " + authType);
        }

        if (!authHandlerFactory.supports(authType)) {
            throw new IllegalArgumentException("未知的认证类型: " + authType);
        }
        AuthHandler handler = authHandlerFactory.getHandler(authType);
        if (handler == null) {
            throw new IllegalArgumentException("未知的认证类型: " + authType);
        }
        handler.applyAuth(builder, authConfig, Collections.singletonMap("vendorCode", vendorCode));
        log.debug("应用认证: type={}", authType);
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
        Map<String, Object> vendorResponse = new LinkedHashMap<>();
        try {
            vendorResponse.putAll(objectMapper.readValue(rawBody,
                    new TypeReference<Map<String, Object>>() {}));
        } catch (IOException parseError) {
            if (!hasEnabledSecuritySteps(config, SecurityDirection.RESPONSE)) {
                throw parseError;
            }
            log.debug("响应体不是JSON对象，将先执行响应安全流水线");
        }

        if (hasEnabledSecuritySteps(config, SecurityDirection.RESPONSE)) {
            Map<String, String> responseHeaders = new LinkedHashMap<>();
            response.headers().toMultimap().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    responseHeaders.put(name, values.get(values.size() - 1));
                }
            });
            SecurityExecutionContext responseContext = new SecurityExecutionContext(
                    SecurityDirection.RESPONSE,
                    vendorResponse,
                    responseHeaders,
                    new LinkedHashMap<>(),
                    config.getResolvedSecrets());
            responseContext.setBody(rawBody);
            securityPipelineExecutor.execute(SecurityDirection.RESPONSE, config.getSecuritySteps(), responseContext);
            if (!Objects.equals(rawBody, responseContext.getBody())) {
                Map<String, Object> decryptedBody = objectMapper.readValue(responseContext.getBody(),
                        new TypeReference<Map<String, Object>>() {});
                responseContext.getParams().clear();
                responseContext.getParams().putAll(decryptedBody);
            }
            vendorResponse = responseContext.getParams();
        }

        Map<String, Object> transformedResponse = transformResponse(vendorResponse, config.getResponseMapping());

        return new VendorCallResult(true, transformedResponse, logResponse, null, null, latency);
    }

    private boolean hasEnabledSecuritySteps(VendorAdapterConfig config, SecurityDirection direction) {
        return config.getSecuritySteps() != null && config.getSecuritySteps().stream()
                .anyMatch(step -> step != null
                        && step.getDirection() == direction
                        && !Boolean.FALSE.equals(step.getEnabled()));
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
