package com.dataplatform.common.constant;

/**
 * 公共 Web 层的 Trace Constants。
 * <p>组件，封装 Trace Constants 相关职责。</p>
 */
public final class TraceConstants {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private TraceConstants() {}
}
