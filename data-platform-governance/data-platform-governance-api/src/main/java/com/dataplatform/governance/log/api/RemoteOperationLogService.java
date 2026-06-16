package com.dataplatform.governance.log.api;

import com.dataplatform.common.log.OperationLogRecord;
import com.dataplatform.common.log.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Remote operation log service that sends logs to the log service via Feign.
 * Auto-configured when LogClient is available.
 */
@Service
public class RemoteOperationLogService implements OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(RemoteOperationLogService.class);

    @Autowired(required = false)
    private LogClient logClient;

    @Override
    public void save(OperationLogRecord record) {
        if (logClient == null) {
            log.warn("LogClient is null, operation log will not be saved");
            return;
        }

        Map<String, Object> logData = new HashMap<>();
        if (record.getUserId() != null) {
            logData.put("userId", record.getUserId());
        }
        logData.put("username", record.getUsername());
        logData.put("module", record.getModule());
        logData.put("operation", record.getOperation());
        logData.put("method", record.getMethod());
        logData.put("params", record.getParams());
        logData.put("result", record.getResult());
        logData.put("ip", record.getIp());
        logData.put("location", record.getLocation());
        if (record.getDuration() != null) {
            logData.put("duration", record.getDuration());
        }
        logData.put("status", record.getStatus());

        try {
            log.debug("Saving operation log via Feign: module={}, operation={}", record.getModule(), record.getOperation());
            logClient.saveLog(logData);
            log.debug("Operation log saved successfully");
        } catch (Exception e) {
            log.error("Failed to save operation log: {}", e.getMessage(), e);
        }
    }
}
