package com.dataplatform.access.approval.api;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicationUpsertRequest(
        String requestType,
        Long callerId,
        Long apiKeyId,
        List<Long> interfaceIds,
        String businessPurpose,
        String businessScene,
        Long expectedDailyCalls,
        LocalDateTime requestedExpireAt,
        String ticketNo) {
}
