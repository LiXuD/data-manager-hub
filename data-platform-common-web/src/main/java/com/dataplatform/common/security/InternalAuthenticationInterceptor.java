package com.dataplatform.common.security;

import com.dataplatform.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class InternalAuthenticationInterceptor implements HandlerInterceptor {

    public static final String PRINCIPAL_ATTRIBUTE = InternalPrincipal.class.getName();
    private static final String BEARER_PREFIX = "Bearer ";

    private final InternalJwtService jwtService;
    private final ObjectMapper objectMapper;

    public InternalAuthenticationInterceptor(InternalJwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeError(response, 401, "内部服务认证缺失");
            return false;
        }
        try {
            InternalPrincipal principal = jwtService.verify(header.substring(BEARER_PREFIX.length()));
            String requiredScope = requiredScope(handler);
            if (requiredScope == null || !principal.scopes().contains(requiredScope)) {
                writeError(response, 403, "内部服务权限不足");
                return false;
            }
            request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
            request.setAttribute(InternalActorContext.ACTOR_ID_ATTRIBUTE,
                    request.getHeader(InternalActorContext.ACTOR_ID_HEADER));
            request.setAttribute(InternalActorContext.TENANT_ID_ATTRIBUTE,
                    request.getHeader(InternalActorContext.TENANT_ID_HEADER));
            return true;
        } catch (Exception e) {
            writeError(response, 401, "内部服务凭证无效");
            return false;
        }
    }

    private String requiredScope(Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return null;
        }
        InternalScope annotation = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), InternalScope.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), InternalScope.class);
        }
        return annotation != null ? annotation.value() : null;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
