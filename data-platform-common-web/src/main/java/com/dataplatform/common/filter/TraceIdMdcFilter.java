package com.dataplatform.common.filter;

import com.dataplatform.common.constant.TraceConstants;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class TraceIdMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TraceConstants.TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TraceConstants.TRACE_ID_MDC_KEY, traceId);
            response.setHeader(TraceConstants.TRACE_ID_HEADER, traceId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceConstants.TRACE_ID_MDC_KEY);
        }
    }
}
