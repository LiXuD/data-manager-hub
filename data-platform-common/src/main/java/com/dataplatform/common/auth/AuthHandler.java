package com.dataplatform.common.auth;

import java.util.Map;

/**
 * 认证处理器接口
 * 用于处理不同类型的 API 认证
 */
public interface AuthHandler {

    /**
     * 获取认证类型
     * @return 认证类型标识 (NONE, BASIC, BEARER, API_KEY)
     */
    String getAuthType();

    /**
     * 应用认证信息到请求
     *
     * @param builder 请求构建器（可以是 HTTP 请求构建器或其他类型）
     * @param authConfig 认证配置 (JSON 解析后的 Map)
     * @param context 上下文信息（可包含变量值等）
     */
    void applyAuth(Object builder, Map<String, Object> authConfig, Map<String, String> context);

    /**
     * 验证认证配置是否有效
     *
     * @param authConfig 认证配置
     * @return 是否有效
     */
    boolean validateConfig(Map<String, Object> authConfig);
}
