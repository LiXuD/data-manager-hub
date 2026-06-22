package com.dataplatform.common.filter;

import com.dataplatform.common.constant.TraceConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * HTTP 请求/响应报文日志记录过滤器。
 * <p>记录每一笔 HTTP 请求和响应的原始报文，用于全链路监控和问题排查。</p>
 */
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);
    private static final int MAX_BODY_LENGTH = 4096;
    private static final String[] SKIP_PATTERNS = {"/actuator/", "/health/", "/favicon.ico"};
    private static final Set<String> NO_BODY_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (shouldSkip(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean hasBody = !NO_BODY_METHODS.contains(request.getMethod());
        ContentCachingRequestWrapper requestWrapper = hasBody ? new ContentCachingRequestWrapper(request) : null;
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String queryString = request.getQueryString();

        try {
            if (requestWrapper != null) {
                filterChain.doFilter(requestWrapper, responseWrapper);
            } else {
                filterChain.doFilter(request, responseWrapper);
            }
        } finally {
            if (log.isInfoEnabled()) {
                long duration = System.currentTimeMillis() - startTime;
                int statusCode = responseWrapper.getStatus();
                String traceId = MDC.get(TraceConstants.TRACE_ID_MDC_KEY);
                String requestBody = requestWrapper != null ? readBody(requestWrapper.getContentAsByteArray()) : "[no-body]";
                String responseBody = readBody(responseWrapper.getContentAsByteArray());

                log.info("[HTTP] {} {} | traceId={} | status={} | {}ms | req={} | res={}",
                        method,
                        uri + (queryString != null ? "?" + queryString : ""),
                        traceId != null ? traceId : "-",
                        statusCode,
                        duration,
                        requestBody,
                        responseBody);
            }

            responseWrapper.copyBodyToResponse();
        }
    }

    private boolean shouldSkip(String uri) {
        if (uri == null) {
            return true;
        }
        for (String pattern : SKIP_PATTERNS) {
            if (uri.startsWith(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String readBody(byte[] body) {
        if (body.length == 0) {
            return "[empty]";
        }
        String str = new String(body, StandardCharsets.UTF_8);
        if (str.length() <= MAX_BODY_LENGTH) {
            return str;
        }
        return str.substring(0, MAX_BODY_LENGTH) + "...[truncated " + str.length() + " chars]";
    }
}
