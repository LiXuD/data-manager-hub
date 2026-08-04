package com.dataplatform.access.connector.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps local runtime readiness aligned with active connector bindings. */
@Component
public class ConnectorRuntimeStartupSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(ConnectorRuntimeStartupSynchronizer.class);

    private final ConnectorPluginActivationService activationService;

    public ConnectorRuntimeStartupSynchronizer(ConnectorPluginActivationService activationService) {
        this.activationService = activationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        synchronize();
    }

    @Scheduled(fixedDelayString = "${connector.runtime.required-sync-interval-ms:30000}")
    public void synchronize() {
        try {
            activationService.synchronizeRequiredArtifacts();
        } catch (RuntimeException ex) {
            log.warn("连接器活动版本同步失败，readiness保持关闭: errorCode={}",
                    activationService.readinessErrorCode());
        }
    }
}
