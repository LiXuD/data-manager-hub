package com.dataplatform.common.filter;

import com.dataplatform.common.constant.TraceConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;

/**
 * HTTP 请求/响应报文日志记录过滤器。
 * <p>记录每一笔 HTTP 请求和响应的原始报文，用于全链路监控和问题排查。</p>
 */
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);
    private static final int MAX_BODY_LENGTH = 4096;
    private static final String[] SKIP_PATTERNS = {"/actuator/", "/health/", "/favicon.ico"};

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (shouldSkip(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        String traceId = request.getHeader(TraceConstants.TRACE_ID_HEADER);
        String method = request.getMethod();
        String queryString = request.getQueryString();

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = responseWrapper.getStatus();

            String requestBody = getRequestBody(requestWrapper);
            String responseBody = getResponseBody(responseWrapper);

            log.info("[HTTP] {} {} | traceId={} | status={} | {}ms\n  Request: {}\n  Response: {}",
                    method,
                    uri + (queryString != null ? "?" + queryString : ""),
                    traceId != null ? traceId : "-",
                    statusCode,
                    duration,
                    requestBody,
                    responseBody);

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

    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] body = request.getContentAsByteArray();
        if (body.length == 0) {
            return "[empty]";
        }
        String bodyStr = new String(body, StandardCharsets.UTF_8);
        return truncate(bodyStr);
    }

    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] body = response.getContentAsByteArray();
        if (body.length == 0) {
            return "[empty]";
        }
        String bodyStr = new String(body, StandardCharsets.UTF_8);
        return truncate(bodyStr);
    }

    private String truncate(String str) {
        if (str.length() <= MAX_BODY_LENGTH) {
            return str;
        }
        return str.substring(0, MAX_BODY_LENGTH) + "...[truncated " + str.length() + " chars]";
    }
}
