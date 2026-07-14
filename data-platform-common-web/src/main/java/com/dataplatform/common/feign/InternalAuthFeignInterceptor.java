package com.dataplatform.common.feign;

import com.dataplatform.common.security.ServiceTokenProvider;
import com.dataplatform.common.security.InternalActorContext;
import com.dataplatform.common.security.InternalFeignContract;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class InternalAuthFeignInterceptor implements RequestInterceptor {

    private final ServiceTokenProvider tokenProvider;

    public InternalAuthFeignInterceptor(ServiceTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (template.feignTarget() == null
                || !template.feignTarget().type().isAnnotationPresent(InternalFeignContract.class)) {
            return;
        }
        String audience = template.feignTarget().name();
        template.header("Authorization", "Bearer " + tokenProvider.getToken(audience));
        propagateActorContext(template);
    }

    private void propagateActorContext(RequestTemplate template) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        addIfPresent(template, InternalActorContext.ACTOR_ID_HEADER,
                request.getAttribute(InternalActorContext.ACTOR_ID_ATTRIBUTE));
        addIfPresent(template, InternalActorContext.TENANT_ID_HEADER,
                request.getAttribute(InternalActorContext.TENANT_ID_ATTRIBUTE));
    }

    private void addIfPresent(RequestTemplate template, String header, Object value) {
        if (value != null) {
            template.header(header, value.toString());
        }
    }
}
