package com.dataplatform.common.security;

import com.dataplatform.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class InternalAuthenticationInterceptor implements HandlerInterceptor {

    public static final String PRINCIPAL_ATTRIBUTE = InternalPrincipal.class.getName();
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Logger log = LoggerFactory.getLogger(InternalAuthenticationInterceptor.class);

    private final InternalJwtService jwtService;
    private final ObjectMapper objectMapper;
    private final BeanFactory beanFactory;

    public InternalAuthenticationInterceptor(
            InternalJwtService jwtService, ObjectMapper objectMapper, BeanFactory beanFactory) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.beanFactory = beanFactory;
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
                log.warn("Internal authorization denied: service={}, requiredScope={}, grantedScopes={}, handler={}",
                        principal.serviceName(), requiredScope, principal.scopes(), handler);
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
        Class<?> targetClass = resolveTargetClass(method);
        Method targetMethod = AopUtils.getMostSpecificMethod(method.getMethod(), targetClass);
        InternalScope annotation = AnnotatedElementUtils.findMergedAnnotation(targetMethod, InternalScope.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, InternalScope.class);
        }
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(
                    method.getMethod().getDeclaringClass(), InternalScope.class);
        }
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), InternalScope.class);
        }
        return annotation != null ? annotation.value() : null;
    }

    private Class<?> resolveTargetClass(HandlerMethod method) {
        if (method.getBean() instanceof String beanName) {
            Class<?> beanType = beanFactory.getType(beanName);
            return beanType != null ? beanType : method.getBeanType();
        }
        return AopUtils.getTargetClass(method.getBean());
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
