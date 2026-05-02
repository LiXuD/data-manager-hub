package com.dataplatform.common.auth;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证处理器工厂
 * 根据认证类型获取对应的处理器
 */
@Component
public class AuthHandlerFactory {

    private final Map<String, AuthHandler> handlers = new HashMap<>();

    public AuthHandlerFactory() {
        // 注册默认处理器
        registerHandler(new NoneAuthHandler());
        registerHandler(new BasicAuthHandler());
        registerHandler(new BearerAuthHandler());
        registerHandler(new ApiKeyAuthHandler());
    }

    /**
     * 注册认证处理器
     */
    public void registerHandler(AuthHandler handler) {
        handlers.put(handler.getAuthType(), handler);
    }

    /**
     * 获取认证处理器
     *
     * @param authType 认证类型
     * @return 对应的处理器，如果不存在则返回 NoneAuthHandler
     */
    public AuthHandler getHandler(String authType) {
        if (authType == null) {
            return handlers.get(NoneAuthHandler.AUTH_TYPE);
        }
        return handlers.getOrDefault(authType.toUpperCase(), handlers.get(NoneAuthHandler.AUTH_TYPE));
    }

    /**
     * 检查是否支持指定的认证类型
     */
    public boolean supports(String authType) {
        return handlers.containsKey(authType != null ? authType.toUpperCase() : null);
    }
}
