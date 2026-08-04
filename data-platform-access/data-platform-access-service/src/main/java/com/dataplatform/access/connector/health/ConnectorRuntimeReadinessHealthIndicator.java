package com.dataplatform.access.connector.health;

import com.dataplatform.access.connector.service.ConnectorPluginActivationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("connectorRuntimeReadiness")
public class ConnectorRuntimeReadinessHealthIndicator implements HealthIndicator {

    private final ConnectorPluginActivationService activationService;

    public ConnectorRuntimeReadinessHealthIndicator(ConnectorPluginActivationService activationService) {
        this.activationService = activationService;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("instanceId", activationService.localInstanceId());
        String errorCode = activationService.readinessErrorCode();
        if (errorCode != null) {
            details.put("safeErrorCode", errorCode);
        }
        return activationService.isReady()
                ? Health.up().withDetails(details).build()
                : Health.down().withDetails(details).build();
    }
}
