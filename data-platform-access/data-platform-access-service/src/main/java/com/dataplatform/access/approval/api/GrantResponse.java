package com.dataplatform.access.approval.api;

import java.time.LocalDateTime;

public record GrantResponse(
        Long id,
        Long tenantId,
        Long callerId,
        String callerName,
        Long apiKeyId,
        String apiKeyName,
        Long interfaceId,
        String interfaceCode,
        String interfaceName,
        String source,
        String status,
        LocalDateTime effectiveAt,
        LocalDateTime expireAt,
        LocalDateTime revokedAt,
        Long revokedBy,
        String revokeReason) {
}
