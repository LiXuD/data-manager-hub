package com.dataplatform.access.approval.api;

import java.time.LocalDateTime;

public record ApiKeyOptionResponse(
        Long id,
        Long callerId,
        String keyName,
        String status,
        LocalDateTime expireTime) {
}
