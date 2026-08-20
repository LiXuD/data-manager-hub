package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.CancellationToken;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Read-only cancellation view owned by the connector host. */
public final class HostCancellationToken implements CancellationToken {

    private final BooleanSupplier cancellationRequested;

    public HostCancellationToken(BooleanSupplier cancellationRequested) {
        this.cancellationRequested = Objects.requireNonNull(
                cancellationRequested, "cancellationRequested");
    }

    @Override public boolean isCancelled() { return cancellationRequested.getAsBoolean(); }

    @Override
    public void throwIfCancelled() throws ConnectorException {
        if (isCancelled()) {
            throw new ConnectorException(ErrorCategory.PLUGIN_INTERNAL_ERROR, "REQUEST_CANCELLED",
                    "Connector execution was cancelled", RequestDeliveryState.NOT_SENT);
        }
    }
}
