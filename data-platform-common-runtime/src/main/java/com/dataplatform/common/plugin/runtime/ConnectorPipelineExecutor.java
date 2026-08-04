package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.StageTiming;
import com.dataplatform.plugin.spi.TransportStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ConnectorPipelineExecutor {

    private final Clock clock;
    private final com.dataplatform.plugin.spi.PluginLogger logger;
    private final com.dataplatform.plugin.spi.PluginMetricRecorder metrics;
    private final ConnectorStageSecretScope secretScope;

    public ConnectorPipelineExecutor(Clock clock,
                                     com.dataplatform.plugin.spi.PluginLogger logger,
                                     com.dataplatform.plugin.spi.PluginMetricRecorder metrics) {
        this(clock, logger, metrics, ConnectorStageSecretScope.NO_OP);
    }

    public ConnectorPipelineExecutor(Clock clock,
                                     com.dataplatform.plugin.spi.PluginLogger logger,
                                     com.dataplatform.plugin.spi.PluginMetricRecorder metrics,
                                     ConnectorStageSecretScope secretScope) {
        this.clock = clock;
        this.logger = logger;
        this.metrics = metrics;
        this.secretScope = secretScope;
    }

    public ConnectorExecutionResult execute(CompiledConnectorPipeline pipeline,
                                            ConnectorExecutionRequest request) {
        DefaultConnectorExchange exchange = new DefaultConnectorExchange(request, pipeline.definition());
        DefaultStageExecutionContext context = new DefaultStageExecutionContext(clock, request.deadline(),
                request.cancellationRequested(), logger, metrics);
        List<StageTiming> timings = new ArrayList<>();
        CompiledPipelineStep current = null;
        try {
            for (CompiledPipelineStep step : pipeline.steps()) {
                current = step;
                ensureExecutable(exchange);
                Instant started = clock.instant();
                boolean successful = false;
                exchange.enter(step.definition().capability());
                if (step.definition().capability() == StageCapability.TRANSPORT) {
                    if (exchange.request() == null) {
                        throw new ConnectorException(ErrorCategory.REQUEST_BUILD_ERROR, "REQUEST_NOT_BUILT",
                                "Transport stage requires a request", RequestDeliveryState.NOT_SENT);
                    }
                    exchange.transportAttempted();
                }
                try {
                    secretScope.enter(step.definition().config());
                    step.lease().handle().withContextClassLoader(() -> {
                        step.stage().execute(exchange, context);
                        return null;
                    });
                    if (step.definition().capability() == StageCapability.TRANSPORT) {
                        if (exchange.rawResponse() == null) {
                            throw new ConnectorException(ErrorCategory.PLUGIN_INTERNAL_ERROR,
                                    "TRANSPORT_RESPONSE_MISSING", "Transport did not provide a response",
                                    RequestDeliveryState.MAYBE_SENT);
                        }
                        exchange.transportCompleted();
                    }
                    successful = true;
                } catch (ConnectorException exception) {
                    throw exception;
                } catch (Exception exception) {
                    if (exception.getCause() instanceof ConnectorException connectorException) {
                        throw connectorException;
                    }
                    throw new ConnectorException(ErrorCategory.PLUGIN_INTERNAL_ERROR,
                            "PLUGIN_STAGE_ERROR", "Plugin stage failed",
                            deliveryForFailure(exchange, step.definition().capability()), exception);
                } finally {
                    secretScope.leave();
                    exchange.leave();
                    timings.add(new StageTiming(step.definition().stageKey(), step.definition().capability(),
                            step.definition().pluginId(), step.definition().pluginVersion(),
                            Duration.between(started, clock.instant()), successful));
                }
            }
            if (!exchange.normalizedDataProduced()) {
                throw new ConnectorException(ErrorCategory.CONTRACT_VIOLATION, "NORMALIZED_DATA_MISSING",
                        "Pipeline did not produce normalized data", exchange.deliveryState());
            }
            TransportStatus transport = transportStatus(exchange);
            if (transport == TransportStatus.HTTP_ERROR) {
                return failure(exchange, current, timings, ErrorCategory.TRANSPORT_HTTP_ERROR,
                        "TRANSPORT_HTTP_ERROR", "Vendor returned an unsuccessful HTTP status", transport,
                        exchange.deliveryState());
            }
            BusinessStatus business = exchange.businessStatus() == BusinessStatus.NOT_EVALUATED
                    ? BusinessStatus.SUCCESS : exchange.businessStatus();
            return new ConnectorExecutionResult(transport, business, exchange.normalizedData(), null,
                    null, null, exchange.billingSignal(), exchange.cacheSignal(), exchange.deliveryState(),
                    transportPluginId(pipeline), transportPluginVersion(pipeline),
                    pipeline.definition().pipelineVersion(), pipeline.definition().snapshotHash(), timings);
        } catch (ConnectorException exception) {
            return failure(exchange, current, timings, exception.category(), exception.errorCode(),
                    exception.safeMessage(), mapTransportStatus(exception.category()), exception.deliveryState());
        } catch (RuntimeException exception) {
            RequestDeliveryState delivery = current == null ? RequestDeliveryState.NOT_SENT
                    : deliveryForFailure(exchange, current.definition().capability());
            return failure(exchange, current, timings, ErrorCategory.PLUGIN_INTERNAL_ERROR,
                    "PIPELINE_RUNTIME_ERROR", "Connector pipeline failed",
                    TransportStatus.FAILED, delivery);
        }
    }

    private void ensureExecutable(DefaultConnectorExchange exchange) throws ConnectorException {
        if (exchange.cancellationRequested()) {
            throw new ConnectorException(ErrorCategory.PLUGIN_INTERNAL_ERROR, "REQUEST_CANCELLED",
                    "Connector execution was cancelled", exchange.deliveryState());
        }
        if (!clock.instant().isBefore(exchange.deadline())) {
            throw new ConnectorException(ErrorCategory.TRANSPORT_TIMEOUT, "EXECUTION_DEADLINE_EXCEEDED",
                    "Connector execution deadline was exceeded", exchange.deliveryState());
        }
    }

    private RequestDeliveryState deliveryForFailure(DefaultConnectorExchange exchange, StageCapability capability) {
        if (capability == StageCapability.TRANSPORT && exchange.deliveryState() == RequestDeliveryState.NOT_SENT) {
            return RequestDeliveryState.MAYBE_SENT;
        }
        return exchange.deliveryState();
    }

    private TransportStatus transportStatus(DefaultConnectorExchange exchange) {
        if (exchange.rawResponse() == null) {
            return TransportStatus.NOT_ATTEMPTED;
        }
        int status = exchange.rawResponse().statusCode();
        return status >= 200 && status < 300 ? TransportStatus.SUCCESS : TransportStatus.HTTP_ERROR;
    }

    private TransportStatus mapTransportStatus(ErrorCategory category) {
        return switch (category) {
            case TRANSPORT_TIMEOUT -> TransportStatus.TIMEOUT;
            case TRANSPORT_CONNECTION_ERROR -> TransportStatus.CONNECTION_ERROR;
            case TRANSPORT_HTTP_ERROR -> TransportStatus.HTTP_ERROR;
            default -> TransportStatus.FAILED;
        };
    }

    private ConnectorExecutionResult failure(DefaultConnectorExchange exchange, CompiledPipelineStep current,
                                             List<StageTiming> timings, ErrorCategory category,
                                             String code, String message, TransportStatus transport,
                                             RequestDeliveryState delivery) {
        return new ConnectorExecutionResult(transport, BusinessStatus.UNKNOWN, java.util.Map.of(), category,
                code, message, BillingSignal.UNKNOWN, CacheSignal.NOT_CACHEABLE, delivery,
                current == null ? null : current.definition().pluginId(),
                current == null ? null : current.definition().pluginVersion(),
                exchange.pipelineVersion(), exchange.snapshotHash(), timings);
    }

    private String transportPluginId(CompiledConnectorPipeline pipeline) {
        return pipeline.steps().stream().filter(step -> step.definition().capability() == StageCapability.TRANSPORT)
                .findFirst().orElseThrow().definition().pluginId();
    }

    private String transportPluginVersion(CompiledConnectorPipeline pipeline) {
        return pipeline.steps().stream().filter(step -> step.definition().capability() == StageCapability.TRANSPORT)
                .findFirst().orElseThrow().definition().pluginVersion();
    }
}
