package com.dataplatform.common.util;

import com.dataplatform.common.constant.TraceConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Bridges custom X-Trace-Id with SkyWalking native trace IDs.
 * <p>
 * Uses conditional class loading to avoid hard dependency on
 * {@code apm-toolkit-trace}. Works gracefully when SkyWalking agent
 * is NOT loaded — all SkyWalking calls are no-ops.
 */
public final class TraceContextBridge {

    private static final Logger log = LoggerFactory.getLogger(TraceContextBridge.class);

    /** MDC key for the SkyWalking trace ID (alongside custom "traceId"). */
    public static final String SW_TRACE_ID_MDC_KEY = "swTraceId";

    private static final Method TRACE_ID_METHOD;
    private static final boolean SKYWALKING_AVAILABLE;

    static {
        Method method = null;
        boolean available = false;
        try {
            Class<?> clazz = Class.forName("org.apache.skywalking.apm.toolkit.trace.TraceContext");
            method = clazz.getMethod("traceId");
            available = true;
            log.info("[TraceContextBridge] SkyWalking TraceContext available — trace enrichment enabled");
        } catch (ClassNotFoundException e) {
            log.info("[TraceContextBridge] SkyWalking TraceContext not available — using custom trace only");
        } catch (NoSuchMethodException e) {
            log.warn("[TraceContextBridge] TraceContext found but traceId() method missing — version mismatch?");
        }
        TRACE_ID_METHOD = method;
        SKYWALKING_AVAILABLE = available;
    }

    private TraceContextBridge() {}

    /**
     * Returns the best available trace ID:
     * <ol>
     *   <li>SkyWalking {@code TraceContext.traceId()} if agent is loaded</li>
     *   <li>MDC "{@code traceId}" (custom X-Trace-Id) as fallback</li>
     *   <li>Generated UUID if neither is available</li>
     * </ol>
     */
    public static String getCurrentTraceId() {
        if (SKYWALKING_AVAILABLE) {
            try {
                String swTraceId = (String) TRACE_ID_METHOD.invoke(null);
                if (swTraceId != null && !swTraceId.isEmpty()) {
                    return swTraceId;
                }
            } catch (Exception e) {
                log.debug("[TraceContextBridge] Failed to get SkyWalking traceId: {}", e.getMessage());
            }
        }
        String mdcTraceId = MDC.get(TraceConstants.TRACE_ID_MDC_KEY);
        if (mdcTraceId != null && !mdcTraceId.isBlank()) {
            return mdcTraceId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Enriches the current MDC with SkyWalking trace ID if available.
     * Safe to call even when SkyWalking agent is not loaded.
     *
     * @return the SkyWalking trace ID, or {@code null} if unavailable
     */
    public static String enrichMdcWithSkyWalkingTraceId() {
        if (!SKYWALKING_AVAILABLE) {
            return null;
        }
        try {
            String swTraceId = (String) TRACE_ID_METHOD.invoke(null);
            if (swTraceId != null && !swTraceId.isEmpty()) {
                MDC.put(SW_TRACE_ID_MDC_KEY, swTraceId);
                return swTraceId;
            }
        } catch (Exception e) {
            log.debug("[TraceContextBridge] Failed to enrich MDC with SkyWalking trace: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Returns whether SkyWalking agent trace integration is active.
     */
    public static boolean isSkyWalkingAvailable() {
        return SKYWALKING_AVAILABLE;
    }
}
