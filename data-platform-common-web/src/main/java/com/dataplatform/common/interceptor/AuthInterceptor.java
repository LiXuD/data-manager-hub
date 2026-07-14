package com.dataplatform.common.interceptor;

import com.dataplatform.common.security.InternalActorContext;
import com.dataplatform.common.result.Result;
import com.dataplatform.common.util.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 认证拦截器 - 验证请求的Authorization token
 */
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 不需要认证的路径
    private static final List<String> EXCLUDE_PATHS = Arrays.asList(
        "/auth/login",
        "/auth/verify",
        "/actuator",
        "/health"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 检查是否是不需要认证的路径
        if (isExcludePath(path)) {
            return true;
        }

        String authHeader = request.getHeader(AUTH_HEADER);

        // 检查是否有Authorization头
        if (isBlank(authHeader)) {
            log.warn("请求路径: {} 缺少Authorization头", path);
            sendUnauthorizedResponse(response, "缺少认证信息");
            return false;
        }

        // 检查Bearer token格式
        if (!authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("请求路径: {} Authorization格式错误", path);
            sendUnauthorizedResponse(response, "认证格式错误");
            return false;
        }

        if (isBlank(authHeader.substring(BEARER_PREFIX.length()))) {
            log.warn("请求路径: {} token为空", path);
            sendUnauthorizedResponse(response, "token为空");
            return false;
        }

        try {
            if (!UserContext.isLoggedIn()) {
                sendUnauthorizedResponse(response, "会话已过期");
                return false;
            }
            Long userId = UserContext.getCurrentUserId();
            Long tenantId = UserContext.getCurrentTenantId();
            request.setAttribute("username", UserContext.getCurrentUsername());
            request.setAttribute(InternalActorContext.ACTOR_ID_ATTRIBUTE, userId);
            request.setAttribute(InternalActorContext.TENANT_ID_ATTRIBUTE, tenantId);
            return true;
        } catch (Exception e) {
            log.warn("请求路径: {} token校验失败", path);
            sendUnauthorizedResponse(response, "无效或已过期的token");
            return false;
        }
    }

    private boolean isExcludePath(String path) {
        for (String excludePath : EXCLUDE_PATHS) {
            if (path.contains(excludePath)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Result<?> errorResult = Result.error(401, "未授权: " + message);
        response.getWriter().write(objectMapper.writeValueAsString(errorResult));
    }
}
