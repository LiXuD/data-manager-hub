package com.dataplatform.billing.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class BillingPeriodResolver {

    public LocalDate resolve(LocalDateTime callTime, String cycle, String timezone) {
        ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone);
        LocalDate date = (callTime != null ? callTime : LocalDateTime.now()).toLocalDate();
        return switch (normalize(cycle)) {
            case "DAY" -> date;
            case "YEAR" -> date.withDayOfYear(1);
            default -> date.withDayOfMonth(1);
        };
    }

    private String normalize(String value) {
        return value == null ? "MONTH" : value.toUpperCase();
    }
}
