package com.dataplatform.access.approval.api;

import java.time.LocalDateTime;
import java.util.List;

public record EmergencyGrantRequest(
        Long callerId,
        Long apiKeyId,
        List<Long> interfaceIds,
        LocalDateTime expireAt,
        String reason,
        String ticketNo) {
}
