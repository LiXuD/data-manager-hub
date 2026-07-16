package com.dataplatform.governance.monitor.vo;

import java.time.LocalDateTime;

public record ServiceHealthVO(
        String serviceName,
        String status,
        long responseTime,
        double uptime,
        int instanceCount,
        LocalDateTime lastCheck) {
}
