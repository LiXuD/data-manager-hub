package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.PluginValidationContext;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Predicate;

public record DefaultPluginValidationContext(Clock clock, String hostVersion,
                                             Predicate<String> secretReferenceLookup)
        implements PluginValidationContext {

    public DefaultPluginValidationContext {
        Objects.requireNonNull(clock, "clock");
        if (hostVersion == null || hostVersion.isBlank()) {
            throw new IllegalArgumentException("hostVersion is required");
        }
        Objects.requireNonNull(secretReferenceLookup, "secretReferenceLookup");
    }

    @Override
    public boolean secretReferenceExists(String secretRef) {
        return secretReferenceLookup.test(secretRef);
    }
}
