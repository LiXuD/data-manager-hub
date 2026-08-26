package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.BillingSignal;
import com.dataplatform.plugin.spi.AbstractVendorConnectorPlugin;
import com.dataplatform.plugin.spi.BusinessStatus;
import com.dataplatform.plugin.spi.CacheSignal;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorExecutionResult;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.StageCapability;
import com.dataplatform.plugin.spi.StageTiming;
import com.dataplatform.plugin.spi.TransportStatus;
import com.dataplatform.plugin.spi.VendorConnectorStageAdapters;
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
    private final VendorConnectorHostContextFactory hostContextFactory;

    public ConnectorPipelineExecutor(Clock clock,
                                     com.dataplatform.plugin.spi.PluginLogger logger,
                                     com.dataplatform.plugin.spi.PluginMetricRecorder metrics) {
        this(clock, logger, metrics, ConnectorStageSecretScope.NO_OP,
                new VendorConnectorHostContextFactory());
    }

    public ConnectorPipelineExecutor(Clock clock,
                                     com.dataplatform.plugin.spi.PluginLogger logger,
                                     com.dataplatform.plugin.spi.PluginMetricRecorder metrics,
                                     ConnectorStageSecretScope secretScope) {
        this(clock, logger, metrics, secretScope, new VendorConnectorHostContextFactory());
    }

    public ConnectorPipelineExecutor(Clock clock,
                                     com.dataplatform.plugin.spi.PluginLogger logger,
                                     com.dataplatform.plugin.spi.PluginMetricRecorder metrics,
                                     ConnectorStageSecretScope secretScope,
                                     VendorConnectorHostContextFactory hostContextFactory) {
        this.clock = clock;
        this.logger = logger;
        this.metrics = metrics;
        this.secretScope = secretScope;
        this.hostContextFactory = java.util.Objects.requireNonNull(
                hostContextFactory, "hostContextFactory");
    }

    public ConnectorExecutionResult execute(CompiledConnectorPipeline pipeline,
                                            ConnectorExecutionRequest request) {
        return executeWithOutcome(pipeline, request).result();
    }

    /**
     * Executes a pinned pipeline and returns host-only request policy metadata used
     * by Access retry governance. Plugins cannot set retry counts through this API.
     */
    public ConnectorPipelineExecutionOutcome executeWithOutcome(
            CompiledConnectorPipeline pipeline,
            ConnectorExecutionRequest request) {
        try (CompiledConnectorPipeline.RequestLease lease = pipeline.acquire()) {
            return executeWithOutcome(lease, request);
        }
    }

    /** Executes against an already acquired version lease, used by atomic runtime selection. */
    public ConnectorPipelineExecutionOutcome executeWithOutcome(
            CompiledConnectorPipeline.RequestLease lease,
            ConnectorExecutionRequest request) {
        return executeLeased(lease.pipeline(), request);
    }

    private ConnectorPipelineExecutionOutcome executeLeased(
            CompiledConnectorPipeline pipeline,
            ConnectorExecutionRequest request) {
        PluginIdentity auditIdentity = auditIdentity(pipeline);
        DefaultConnectorExchange exchange = new DefaultConnectorExchange(request, pipeline.definition());
        DefaultStageExecutionContext context = new DefaultStageExecutionContext(clock, request.deadline(),
                request.cancellationRequested(), logger, metrics);
        List<StageTiming> timings = new ArrayList<>();
        CompiledPipelineStep current = null;
        boolean currentManagedTransport = false;
        try {
            for (CompiledPipelineStep step : pipeline.steps()) {
                current = step;
                currentManagedTransport = isManagedTransport(step);
                ensureExecutable(exchange);
                Instant started = clock.instant();
                boolean successful = false;
                exchange.enter(step.definition().capability());
                if (step.definition().capability() == StageCapability.TRANSPORT) {
                    if (exchange.request() == null) {
                        throw new ConnectorException(ErrorCategory.REQUEST_BUILD_ERROR, "REQUEST_NOT_BUILT",
                                "Transport stage requires a request", RequestDeliveryState.NOT_SENT);
                    }
                    if (!currentManagedTransport) {
                        exchange.transportAttempted();
                    }
                }
                CompiledPipelineStep.ExecutionStage executionStage = null;
                VendorConnectorStageHostContext vendorContext = null;
                ErrorCategory managedSessionError = null;
                try {
                    executionStage = step.openStage();
                    var stage = executionStage.stage();
                    secretScope.enter(step.definition().config(), step.secretReferences());
                    if (step.lease().handle().plugin() instanceof AbstractVendorConnectorPlugin) {
                        vendorContext = hostContextFactory.create(request, step.definition(),
                                exchange.request(), step.lease().handle());
                    }
                    var stageContext = vendorContext == null ? context : vendorContext;
                    step.lease().handle().withContextClassLoader(() -> {
                        stage.execute(exchange, stageContext);
                        return null;
                    });
                    if (step.definition().capability() == StageCapability.TRANSPORT) {
                        if (currentManagedTransport) {
                            synchronizeManagedDelivery(exchange, vendorContext);
                            if (vendorContext == null
                                    || vendorContext.hostManagedTransportSession() == null
                                    || vendorContext.hostManagedTransportSession().callsAttempted() == 0) {
                                throw new ConnectorException(ErrorCategory.CONTRACT_VIOLATION,
                                        "MANAGED_TRANSPORT_NOT_CALLED",
                                        "Managed connector transport made no host call",
                                        exchange.deliveryState());
                            }
                        }
                        if (exchange.rawResponse() == null) {
                            throw new ConnectorException(ErrorCategory.PLUGIN_INTERNAL_ERROR,
                                    "TRANSPORT_RESPONSE_MISSING", "Transport did not provide a response",
                                    currentManagedTransport ? exchange.deliveryState()
                                            : RequestDeliveryState.MAYBE_SENT);
                        }
                        if (!currentManagedTransport) {
                            exchange.transportCompleted();
                        }
                    }
                    successful = true;
                } catch (ConnectorException exception) {
                    managedSessionError = exception.category();
                    synchronizeManagedDelivery(exchange, vendorContext);
                    throw currentManagedTransport
                            ? withDelivery(exception, exchange.deliveryState()) : exception;
                } catch (Exception exception) {
                    synchronizeManagedDelivery(exchange, vendorContext);
                    if (exception.getCause() instanceof ConnectorException connectorException) {
                        managedSessionError = connectorException.category();
                        throw currentManagedTransport
                                ? withDelivery(connectorException, exchange.deliveryState())
                                : connectorException;
                    }
                    managedSessionError = ErrorCategory.PLUGIN_INTERNAL_ERROR;
                    throw new ConnectorException(ErrorCategory.PLUGIN_INTERNAL_ERROR,
                            "PLUGIN_STAGE_ERROR", "Plugin stage failed",
                            deliveryForFailure(exchange, step.definition().capability(),
                                    currentManagedTransport), exception);
                } finally {
                    synchronizeManagedDelivery(exchange, vendorContext);
                    finishManagedSession(vendorContext,
                            successful ? null : managedSessionError);
                    secretScope.leave();
                    if (executionStage != null) executionStage.close();
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
                return outcome(exchange, failure(pipeline.definition(), exchange, current, timings,
                        ErrorCategory.TRANSPORT_HTTP_ERROR, "TRANSPORT_HTTP_ERROR",
                        "Vendor returned an unsuccessful HTTP status", transport,
                        exchange.deliveryState(), auditIdentity));
            }
            BusinessStatus business = exchange.businessStatus() == BusinessStatus.NOT_EVALUATED
                    ? BusinessStatus.SUCCESS : exchange.businessStatus();
            if (business == BusinessStatus.REJECTED) {
                return outcome(exchange, failure(pipeline.definition(), exchange, current, timings,
                        ErrorCategory.BUSINESS_REJECTED, "BUSINESS_REJECTED",
                        vendorSafeMessage(exchange, "Vendor rejected the request"),
                        transport, exchange.deliveryState(), auditIdentity));
            }
            PluginIdentity resultIdentity = auditIdentity != null
                    ? auditIdentity : transportIdentity(pipeline);
            return outcome(exchange, new ConnectorExecutionResult(transport, business,
                    exchange.normalizedData(), null, null, null, exchange.billingSignal(),
                    exchange.cacheSignal(), exchange.deliveryState(), resultIdentity.pluginId(),
                    resultIdentity.pluginVersion(), pipeline.definition().pipelineVersion(),
                    pipeline.definition().snapshotHash(), pipeline.definition().hashAlgorithm(),
                    pipeline.definition().integrityHash(), timings));
        } catch (ConnectorException exception) {
            TransportStatus transport = failureTransportStatus(exchange, exception.category());
            return outcome(exchange, failure(pipeline.definition(), exchange, current, timings, exception.category(),
                    exception.errorCode(), exception.safeMessage(), transport, exception.deliveryState(),
                    auditIdentity));
        } catch (RuntimeException exception) {
            RequestDeliveryState delivery = current == null ? RequestDeliveryState.NOT_SENT
                    : deliveryForFailure(exchange, current.definition().capability(),
                    currentManagedTransport);
            return outcome(exchange, failure(pipeline.definition(), exchange, current, timings,
                    ErrorCategory.PLUGIN_INTERNAL_ERROR, "PIPELINE_RUNTIME_ERROR",
                    "Connector pipeline failed", failureTransportStatus(
                            exchange, ErrorCategory.PLUGIN_INTERNAL_ERROR), delivery, auditIdentity));
        }
    }

    private ConnectorPipelineExecutionOutcome outcome(
            DefaultConnectorExchange exchange,
            ConnectorExecutionResult result) {
        return new ConnectorPipelineExecutionOutcome(result, requestRetryPermitted(exchange));
    }

    private boolean requestRetryPermitted(DefaultConnectorExchange exchange) {
        return HostIdempotencyContext.fromRequest(exchange.request()).retryPermitted();
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

    private RequestDeliveryState deliveryForFailure(DefaultConnectorExchange exchange,
                                                    StageCapability capability,
                                                    boolean managedTransport) {
        if (capability == StageCapability.TRANSPORT && !managedTransport
                && exchange.deliveryState() == RequestDeliveryState.NOT_SENT) {
            return RequestDeliveryState.MAYBE_SENT;
        }
        return exchange.deliveryState();
    }

    private boolean isManagedTransport(CompiledPipelineStep step) {
        return step.definition().capability() == StageCapability.TRANSPORT
                && step.lease().handle().plugin() instanceof AbstractVendorConnectorPlugin plugin
                && plugin.transportMode()
                == com.dataplatform.plugin.spi.ConnectorTransportMode.HOST_MANAGED_MULTI_HTTP;
    }

    private void synchronizeManagedDelivery(
            DefaultConnectorExchange exchange,
            VendorConnectorStageHostContext context) {
        if (context != null && context.hostManagedTransportSession() != null) {
            exchange.mergeDeliveryState(context.hostManagedTransportSession().deliveryState());
        }
    }

    private void finishManagedSession(
            VendorConnectorStageHostContext context,
            ErrorCategory errorCategory) {
        if (context != null && context.hostManagedTransportSession() != null) {
            context.hostManagedTransportSession().finish(errorCategory == null
                    ? null : errorCategory);
        }
    }

    private ConnectorException withDelivery(
            ConnectorException exception,
            RequestDeliveryState delivery) {
        return exception.deliveryState() == delivery ? exception
                : new ConnectorException(exception.category(), exception.errorCode(),
                exception.safeMessage(), delivery, exception);
    }

    private String vendorSafeMessage(DefaultConnectorExchange exchange, String fallback) {
        Object value = exchange.completedStageOutputs().get(
                VendorConnectorStageAdapters.VENDOR_SAFE_MESSAGE_OUTPUT);
        return value instanceof String text && !text.isBlank() ? text : fallback;
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

    private TransportStatus failureTransportStatus(
            DefaultConnectorExchange exchange, ErrorCategory category) {
        if (category == ErrorCategory.TRANSPORT_TIMEOUT
                || category == ErrorCategory.TRANSPORT_CONNECTION_ERROR
                || category == ErrorCategory.TRANSPORT_HTTP_ERROR) {
            return mapTransportStatus(category);
        }
        if (exchange.rawResponse() != null) {
            return transportStatus(exchange);
        }
        return exchange.deliveryState() == RequestDeliveryState.NOT_SENT
                ? TransportStatus.NOT_ATTEMPTED : TransportStatus.FAILED;
    }

    private ConnectorExecutionResult failure(ConnectorPipelineDefinition definition,
                                             DefaultConnectorExchange exchange, CompiledPipelineStep current,
                                             List<StageTiming> timings, ErrorCategory category,
                                             String code, String message, TransportStatus transport,
                                             RequestDeliveryState delivery,
                                             PluginIdentity auditIdentity) {
        boolean businessRejected = category == ErrorCategory.BUSINESS_REJECTED;
        return new ConnectorExecutionResult(transport,
                businessRejected ? BusinessStatus.REJECTED : BusinessStatus.UNKNOWN,
                businessRejected ? exchange.normalizedData() : java.util.Map.of(), category,
                code, ConnectorSafeMessageSanitizer.sanitize(message, secretScope.sensitiveValues()),
                businessRejected ? exchange.billingSignal() : BillingSignal.UNKNOWN,
                businessRejected ? exchange.cacheSignal() : CacheSignal.NOT_CACHEABLE, delivery,
                auditIdentity != null ? auditIdentity.pluginId()
                        : current == null ? null : current.definition().pluginId(),
                auditIdentity != null ? auditIdentity.pluginVersion()
                        : current == null ? null : current.definition().pluginVersion(),
                exchange.pipelineVersion(), exchange.snapshotHash(),
                definition.hashAlgorithm(), definition.integrityHash(), timings);
    }

    private PluginIdentity auditIdentity(CompiledConnectorPipeline pipeline) {
        CompiledPipelineStep transport = pipeline.steps().stream()
                .filter(step -> step.definition().capability() == StageCapability.TRANSPORT)
                .findFirst().orElseThrow();
        boolean platformTransport = "platform.transport".equals(transport.definition().stageKey())
                && PlatformCoreConnectorMetadata.PLUGIN_ID.equals(transport.definition().pluginId())
                && PlatformCoreConnectorMetadata.VERSION.equals(transport.definition().pluginVersion());
        if (platformTransport) {
            CompiledPipelineStep builder = pipeline.steps().stream()
                    .filter(step -> "connector.request-builder".equals(step.definition().stageKey()))
                    .filter(step -> step.definition().capability() == StageCapability.REQUEST_BUILDER)
                    .filter(step -> step.lease().handle().plugin()
                            instanceof AbstractVendorConnectorPlugin)
                    .findFirst().orElse(null);
            if (builder != null) {
                return new PluginIdentity(builder.definition().pluginId(),
                        builder.definition().pluginVersion());
            }
        }
        return null;
    }

    private PluginIdentity transportIdentity(CompiledConnectorPipeline pipeline) {
        ConnectorStageDefinition transport = pipeline.steps().stream()
                .map(CompiledPipelineStep::definition)
                .filter(step -> step.capability() == StageCapability.TRANSPORT)
                .findFirst().orElseThrow();
        return new PluginIdentity(transport.pluginId(), transport.pluginVersion());
    }

    private record PluginIdentity(String pluginId, String pluginVersion) { }
}
