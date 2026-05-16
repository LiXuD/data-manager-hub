package com.dataplatform.common.auth;

import okhttp3.Request;
import org.springframework.util.StringUtils;

import java.util.Map;

import static com.dataplatform.common.util.VariableSubstitutionUtil.getStringValue;

/**
 * Bearer Token 认证处理器
 * 使用 OAuth2 Bearer Token 认证方式
 */
public class BearerAuthHandler implements AuthHandler {

    public static final String AUTH_TYPE = "BEARER";

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
        String token = getToken(authConfig, context);

        if (StringUtils.hasText(token)) {
            requestBuilder.addHeader("Authorization", "Bearer " + token);
        }
    }

    @Override
    public boolean validateConfig(Map<String, Object> authConfig) {
        if (authConfig == null) return false;
        return StringUtils.hasText(getToken(authConfig, null));
    }

    private String getToken(Map<String, Object> config, Map<String, String> context) {
        return getStringValue(config, "token", context);
    }
}
