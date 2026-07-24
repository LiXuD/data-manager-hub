package com.dataplatform.access.approval.api;

import java.time.LocalDateTime;
import java.util.Map;

public record CompleteTaskRequest(
        Integer applicationVersion,
        String decision,
        LocalDateTime approvedExpireAt,
        String comment,
        Map<String, Object> formData) {
}
