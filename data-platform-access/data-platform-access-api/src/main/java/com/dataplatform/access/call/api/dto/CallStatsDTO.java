package com.dataplatform.access.call.api.dto;

import java.io.Serializable;

public class CallStatsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long totalCalls;
    private Long successCalls;
    private Double avgLatency;
    private Long slowCalls;

    public Long getTotalCalls() { return totalCalls; }
    public void setTotalCalls(Long totalCalls) { this.totalCalls = totalCalls; }
    public Long getSuccessCalls() { return successCalls; }
    public void setSuccessCalls(Long successCalls) { this.successCalls = successCalls; }
    public Double getAvgLatency() { return avgLatency; }
    public void setAvgLatency(Double avgLatency) { this.avgLatency = avgLatency; }
    public Long getSlowCalls() { return slowCalls; }
    public void setSlowCalls(Long slowCalls) { this.slowCalls = slowCalls; }
}
