package com.dataplatform.common.feign;

import com.dataplatform.common.constant.TraceConstants;
import com.dataplatform.common.util.TraceContextBridge;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 公共 Web 层远程调用的 Trace Feign Request Interceptor。
 * <p>请求拦截器，处理认证、上下文或链路增强等横切逻辑。</p>
 */
public class TraceFeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String traceId = null;
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            traceId = request.getHeader(TraceConstants.TRACE_ID_HEADER);
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceContextBridge.getCurrentTraceId();
        }
        if (traceId != null && !traceId.isBlank()) {
            template.header(TraceConstants.TRACE_ID_HEADER, traceId);
        }
    }
}
