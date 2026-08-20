package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.CancellationToken;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.Deadline;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.ManagedHttpTransport;
import com.dataplatform.plugin.spi.ManagedTransportSession;
import com.dataplatform.plugin.spi.PluginMetricRecorder;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.StageExecutionContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Host-owned bounded session for a SIMPLE connector's managed multi-HTTP stage. */
public final class HostManagedTransportSession implements ManagedTransportSession {

    public static final int MAX_CALLS = 5;

    private final ManagedHttpTransport transport;
    private final StageExecutionContext stageContext;
    private final Deadline deadline;
    private final CancellationToken cancellationToken;
    private final PluginMetricRecorder metrics;
    private final Map<String, String> metricTags;
    private final List<ManagedCallFact> callFacts = new ArrayList<>();
    private int calls;
    private RequestDeliveryState deliveryState = RequestDeliveryState.NOT_SENT;
    private boolean sessionMetricRecorded;

    HostManagedTransportSession(
            ManagedHttpTransport transport,
            StageExecutionContext stageContext,
            Deadline deadline,
            CancellationToken cancellationToken,
            PluginMetricRecorder metrics,
            Map<String, String> metricTags) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.stageContext = Objects.requireNonNull(stageContext, "stageContext");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.cancellationToken = Objects.requireNonNull(cancellationToken, "cancellationToken");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.metricTags = Map.copyOf(Objects.requireNonNull(metricTags, "metricTags"));
    }

    @Override
    public synchronized ConnectorRawResponse execute(ConnectorRequest request) throws ConnectorException {
        Objects.requireNonNull(request, "request");
        ensureExecutable();
        if (calls >= MAX_CALLS) {
            throw failure(ErrorCategory.CONTRACT_VIOLATION, "MANAGED_TRANSPORT_CALL_LIMIT",
                    "Managed transport call limit was exceeded", deliveryState, null);
        }
        calls++;
        Instant started = stageContext.clock().instant();
        try {
            ConnectorRawResponse response = transport.execute(request, stageContext);
            if (response == null) {
                merge(RequestDeliveryState.MAYBE_SENT);
                throw failure(ErrorCategory.PLUGIN_INTERNAL_ERROR, "MANAGED_TRANSPORT_RETURNED_NULL",
                        "Managed transport returned no response", deliveryState, null);
            }
            merge(RequestDeliveryState.SENT);
            callFacts.add(new ManagedCallFact(request.url().toString(), response.latency(),
                    response.bytesSent(), response.bytesReceived(), deliveryState, null));
            recordSubrequestMetric("NONE");
            return response;
        } catch (ConnectorException exception) {
            merge(exception.deliveryState());
            callFacts.add(new ManagedCallFact(request.url().toString(),
                    elapsed(started), request.body().length, 0L, deliveryState,
                    exception.category().name()));
            recordSubrequestMetric(exception.category().name());
            throw withAggregateDelivery(exception);
        } catch (RuntimeException exception) {
            merge(RequestDeliveryState.MAYBE_SENT);
            callFacts.add(new ManagedCallFact(request.url().toString(),
                    elapsed(started), request.body().length, 0L, deliveryState,
                    ErrorCategory.PLUGIN_INTERNAL_ERROR.name()));
            recordSubrequestMetric(ErrorCategory.PLUGIN_INTERNAL_ERROR.name());
            throw failure(ErrorCategory.PLUGIN_INTERNAL_ERROR, "MANAGED_TRANSPORT_RUNTIME_ERROR",
                    "Managed transport failed", deliveryState, exception);
        }
    }

    @Override public Deadline deadline() { return deadline; }
    @Override public CancellationToken cancellationToken() { return cancellationToken; }
    @Override public synchronized int remainingCalls() { return MAX_CALLS - calls; }

    synchronized int callsAttempted() { return calls; }
    synchronized RequestDeliveryState deliveryState() { return deliveryState; }
    synchronized List<ManagedCallFact> callFacts() { return List.copyOf(callFacts); }

    synchronized void finish(ErrorCategory errorCategory) {
        if (sessionMetricRecorded) return;
        sessionMetricRecorded = true;
        try {
            Map<String, String> tags = new java.util.LinkedHashMap<>(metricTags);
            tags.put("errorCategory", errorCategory == null ? "NONE" : errorCategory.name());
            metrics.increment("connector_managed_transport_sessions_total", Map.copyOf(tags));
        } catch (RuntimeException ignored) {
            // Observability must not change delivery or business outcomes.
        }
    }

    private void ensureExecutable() throws ConnectorException {
        try {
            cancellationToken.throwIfCancelled();
        } catch (ConnectorException exception) {
            throw withAggregateDelivery(exception);
        }
        if (deadline.isExpired()) {
            throw failure(ErrorCategory.TRANSPORT_TIMEOUT, "EXECUTION_DEADLINE_EXCEEDED",
                    "Connector execution deadline was exceeded", deliveryState, null);
        }
    }

    private void merge(RequestDeliveryState candidate) {
        if (candidate.ordinal() > deliveryState.ordinal()) {
            deliveryState = candidate;
        }
    }

    private ConnectorException withAggregateDelivery(ConnectorException exception) {
        return exception.deliveryState() == deliveryState ? exception
                : failure(exception.category(), exception.errorCode(), exception.safeMessage(),
                deliveryState, exception);
    }

    private Duration elapsed(Instant started) {
        Duration duration = Duration.between(started, stageContext.clock().instant());
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    private void recordSubrequestMetric(String errorCategory) {
        try {
            Map<String, String> tags = new java.util.LinkedHashMap<>(metricTags);
            tags.put("errorCategory", errorCategory);
            metrics.increment("connector_managed_transport_subrequests_total", Map.copyOf(tags));
        } catch (RuntimeException ignored) {
            // Observability must not change delivery or business outcomes.
        }
    }

    private ConnectorException failure(ErrorCategory category, String code, String message,
                                       RequestDeliveryState delivery, Throwable cause) {
        return new ConnectorException(category, code, message, delivery, cause);
    }

    record ManagedCallFact(
            String endpoint,
            Duration latency,
            long bytesSent,
            long bytesReceived,
            RequestDeliveryState deliveryState,
            String errorCategory) {
    }
}
