package com.dataplatform.common.feign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.common.security.InternalActorContext;
import com.dataplatform.common.security.InternalFeignContract;
import com.dataplatform.common.security.ServiceTokenProvider;
import feign.RequestTemplate;
import feign.Target;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class InternalAuthFeignInterceptorTest {

    private final ServiceTokenProvider tokenProvider = mock(ServiceTokenProvider.class);
    private final InternalAuthFeignInterceptor interceptor = new InternalAuthFeignInterceptor(tokenProvider);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void addsServiceTokenAndActorContextToInternalRequest() {
        when(tokenProvider.getToken("data-platform-masterdata")).thenReturn("service-jwt");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(InternalActorContext.ACTOR_ID_ATTRIBUTE, 42L);
        request.setAttribute(InternalActorContext.TENANT_ID_ATTRIBUTE, 7L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = template("/1", InternalClient.class);
        interceptor.apply(template);

        assertEquals("Bearer service-jwt", template.headers().get("Authorization").iterator().next());
        assertEquals("42", template.headers().get(InternalActorContext.ACTOR_ID_HEADER).iterator().next());
        assertEquals("7", template.headers().get(InternalActorContext.TENANT_ID_HEADER).iterator().next());
    }

    @Test
    void neverLeaksServiceTokenToPublicRequest() {
        RequestTemplate template = template("/1", PublicClient.class);

        interceptor.apply(template);

        assertFalse(template.headers().containsKey("Authorization"));
        verify(tokenProvider, never()).getToken("data-platform-masterdata");
    }

    private RequestTemplate template(String path, Class<?> clientType) {
        RequestTemplate template = new RequestTemplate();
        template.feignTarget(new Target.HardCodedTarget<>(clientType,
                "data-platform-masterdata", "http://localhost"));
        template.uri(path);
        return template;
    }

    @InternalFeignContract
    private interface InternalClient {
    }

    private interface PublicClient {
    }
}
