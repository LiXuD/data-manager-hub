package com.dataplatform.common.auth;

import java.util.Map;

/**
 * 无认证处理器
 * 不添加任何认证信息
 */
public class NoneAuthHandler implements AuthHandler {

    public static final String AUTH_TYPE = "NONE";

    @Override
    public String getAuthType() {
        return AUTH_TYPE;
    }

    @Override
    public void applyAuth(Object builder, Map<String, Object> authConfig, Map<String, String> context) {
        // 无需添加认证信息
    }

    @Override
    public boolean validateConfig(Map<String, Object> authConfig) {
        return true;
    }
}
