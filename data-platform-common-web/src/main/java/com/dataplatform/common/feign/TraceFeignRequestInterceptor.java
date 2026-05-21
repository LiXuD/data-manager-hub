package com.dataplatform.common.feign;

import com.dataplatform.common.constant.TraceConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

public class TraceFeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        String traceId = request.getHeader(TraceConstants.TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            template.header(TraceConstants.TRACE_ID_HEADER, traceId);
        }
    }
}
