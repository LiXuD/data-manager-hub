package com.dataplatform.iam.log;

import com.dataplatform.common.log.OperationLogRecord;
import com.dataplatform.common.log.OperationLogService;
import com.dataplatform.log.api.LogClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RemoteOperationLogService implements OperationLogService {

    @Autowired(required = false)
    private LogClient logClient;

    @Override
    public void save(OperationLogRecord record) {
        if (logClient == null) {
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
            logClient.saveLog(logData);
        } catch (Exception e) {
            // 日志保存失败不影响业务
        }
    }
}
