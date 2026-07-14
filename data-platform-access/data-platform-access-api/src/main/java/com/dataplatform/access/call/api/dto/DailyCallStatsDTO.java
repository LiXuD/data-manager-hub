package com.dataplatform.access.call.api.dto;

import java.io.Serializable;
import java.time.LocalDate;

public class DailyCallStatsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private LocalDate date;
    private Long totalCalls;
    private Long successCalls;
    private Double avgLatency;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Long getTotalCalls() { return totalCalls; }
    public void setTotalCalls(Long totalCalls) { this.totalCalls = totalCalls; }
    public Long getSuccessCalls() { return successCalls; }
    public void setSuccessCalls(Long successCalls) { this.successCalls = successCalls; }
    public Double getAvgLatency() { return avgLatency; }
    public void setAvgLatency(Double avgLatency) { this.avgLatency = avgLatency; }
}
