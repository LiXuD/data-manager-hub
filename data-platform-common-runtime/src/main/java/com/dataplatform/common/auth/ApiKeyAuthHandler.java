package com.dataplatform.common.auth;

import okhttp3.HttpUrl;
import okhttp3.Request;
import org.springframework.util.StringUtils;

import java.util.Map;

import static com.dataplatform.common.util.VariableSubstitutionUtil.getStringValue;

/**
 * API Key 认证处理器
 * 支持在请求头或查询参数中传递 API Key
 */
public class ApiKeyAuthHandler implements AuthHandler {

    public static final String AUTH_TYPE = "API_KEY";

    @Override
    public String getAuthType() {
        return AUTH_TYPE;
    }

    @Override
    public void applyAuth(Object builder, Map<String, Object> authConfig, Map<String, String> context) {
        if (!(builder instanceof Request.Builder)) {
            throw new IllegalArgumentException("builder must be Request.Builder");
        }

        Request.Builder requestBuilder = (Request.Builder) builder;

        String keyName = getStringValue(authConfig, "apiKeyName", context);
        String keyValue = getStringValue(authConfig, "apiKeyValue", context);
        String location = getStringValue(authConfig, "apiKeyLocation", context);

        if (!StringUtils.hasText(keyName) || !StringUtils.hasText(keyValue)) {
            return;
        }

        // 默认放在 header 中
        if ("query".equalsIgnoreCase(location)) {
            // 添加到查询参数
            Request currentRequest = requestBuilder.build();
            HttpUrl url = currentRequest.url();
            HttpUrl newUrl = url.newBuilder()
                .addQueryParameter(keyName, keyValue)
                .build();
            requestBuilder.url(newUrl);
        } else {
            // 添加到请求头
            requestBuilder.addHeader(keyName, keyValue);
        }
    }

    @Override
    public boolean validateConfig(Map<String, Object> authConfig) {
        if (authConfig == null) return false;
        return StringUtils.hasText(getStringValue(authConfig, "apiKeyName", null))
            && StringUtils.hasText(getStringValue(authConfig, "apiKeyValue", null));
    }
}
