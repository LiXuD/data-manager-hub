package com.dataplatform.billing.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public record ConnectorBillingObservationDTO(
        long totalEvents,
        long postedEvents,
        long pendingReviewEvents,
        long reversedEvents,
        long billableEvents,
        BigDecimal finalAmount) implements Serializable {
}
