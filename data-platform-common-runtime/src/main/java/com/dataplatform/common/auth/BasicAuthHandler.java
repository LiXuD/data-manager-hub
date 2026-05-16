package com.dataplatform.common.auth;

import okhttp3.Request;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static com.dataplatform.common.util.VariableSubstitutionUtil.getStringValue;

/**
 * Basic Auth 认证处理器
 * 使用 HTTP Basic 认证方式
 */
public class BasicAuthHandler implements AuthHandler {

    public static final String AUTH_TYPE = "BASIC";

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
        String username = getStringValue(authConfig, "username", context);
        String password = getStringValue(authConfig, "password", context);

        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            String credentials = username + ":" + password;
            String encoded = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
            requestBuilder.addHeader("Authorization", "Basic " + encoded);
        }
    }

    @Override
    public boolean validateConfig(Map<String, Object> authConfig) {
        if (authConfig == null) return false;
        return StringUtils.hasText(getStringValue(authConfig, "username", null))
            && StringUtils.hasText(getStringValue(authConfig, "password", null));
    }
}
