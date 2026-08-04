package com.dataplatform.plugin.spi;

import java.time.Clock;

public interface PluginValidationContext {

    Clock clock();

    String hostVersion();

    boolean secretReferenceExists(String secretRef);
}
