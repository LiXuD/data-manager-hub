package com.dataplatform.common.filter;

import com.dataplatform.common.constant.TraceConstants;
import com.dataplatform.common.util.TraceContextBridge;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 公共 Web 层过滤器的 Trace Id Mdc Filter。
 * <p>请求过滤器，处理网关或 Web 链路中的横切逻辑。</p>
 */
public class TraceIdMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TraceConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TraceConstants.TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TraceConstants.TRACE_ID_HEADER, traceId);
        // Enrich MDC with SkyWalking trace ID if agent is active (no-op otherwise)
        TraceContextBridge.enrichMdcWithSkyWalkingTraceId();
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceConstants.TRACE_ID_MDC_KEY);
            MDC.remove(TraceContextBridge.SW_TRACE_ID_MDC_KEY);
        }
    }
}
