package com.dataplatform.common.filter;

import com.dataplatform.common.constant.TraceConstants;
import com.dataplatform.common.util.LogTruncationUtil;
import com.dataplatform.common.util.SensitiveLogSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final String[] SKIP_PATTERNS = {"/actuator/", "/health/", "/favicon.ico"};
    private static final String[] SENSITIVE_RESPONSE_PATTERNS = {
            "/internal/v1/masterdata/vendor-security/",
            "/internal/v1/masterdata/vendor-config/secret-key"
    };
    private static final Set<String> NO_BODY_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final ObjectMapper objectMapper;

    public HttpLoggingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (shouldSkip(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean loggingEnabled = log.isInfoEnabled();
        boolean hasBody = !NO_BODY_METHODS.contains(request.getMethod());

        ContentCachingRequestWrapper requestWrapper = (loggingEnabled && hasBody)
            ? new ContentCachingRequestWrapper(request) : null;
        ContentCachingResponseWrapper responseWrapper = loggingEnabled
            ? new ContentCachingResponseWrapper(response) : null;

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String queryString = request.getQueryString();

        try {
            if (requestWrapper != null && responseWrapper != null) {
                filterChain.doFilter(requestWrapper, responseWrapper);
            } else if (requestWrapper != null) {
                filterChain.doFilter(requestWrapper, response);
            } else if (responseWrapper != null) {
                filterChain.doFilter(request, responseWrapper);
            } else {
                filterChain.doFilter(request, response);
            }
        } finally {
            if (loggingEnabled && responseWrapper != null) {
                long duration = System.currentTimeMillis() - startTime;
                int statusCode = responseWrapper.getStatus();
                String traceId = MDC.get(TraceConstants.TRACE_ID_MDC_KEY);
                String requestBody = requestWrapper != null
                    ? readBody(requestWrapper.getContentAsByteArray()) : "[no-body]";
                String responseBody = isSensitiveResponse(uri)
                        ? "[redacted]"
                        : readBody(responseWrapper.getContentAsByteArray());

                log.info("[HTTP] {} {} | traceId={} | status={} | {}ms | req={} | res={}",
                        method,
                        uri + (queryString != null ? "?" + SensitiveLogSanitizer.sanitizeQueryString(queryString) : ""),
                        traceId != null ? traceId : "-",
                        statusCode,
                        duration,
                        requestBody,
                        responseBody);

                responseWrapper.copyBodyToResponse();
            }
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

    private boolean isSensitiveResponse(String uri) {
        if (uri == null) {
            return false;
        }
        for (String pattern : SENSITIVE_RESPONSE_PATTERNS) {
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
        String sanitized = SensitiveLogSanitizer.sanitizeBody(
                new String(body, StandardCharsets.UTF_8), objectMapper);
        return LogTruncationUtil.truncate(sanitized, LogTruncationUtil.MEDIUM);
    }
}
