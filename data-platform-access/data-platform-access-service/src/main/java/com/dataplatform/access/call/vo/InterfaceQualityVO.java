package com.dataplatform.access.call.vo;

import java.math.BigDecimal;

/**
 * 接口质量报表 VO。
 * 按 vendor_code + data_type + api_code 分组的接口质量指标。
 */
public class InterfaceQualityVO {

    private String vendorCode;
    private String dataType;
    private String apiCode;
    private Long totalCount;
    private Long successCount;
    private Long failCount;
    private Double successRate;
    private Double failRate;
    private Integer avgLatency;
    private Integer p50Latency;
    private Integer p95Latency;
    private Integer p99Latency;
    private Integer maxLatency;
    private BigDecimal totalCost;

    public String getVendorCode() {
        return vendorCode;
    }

    public void setVendorCode(String vendorCode) {
        this.vendorCode = vendorCode;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getApiCode() {
        return apiCode;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public Long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Long successCount) {
        this.successCount = successCount;
    }

    public Long getFailCount() {
        return failCount;
    }

    public void setFailCount(Long failCount) {
        this.failCount = failCount;
    }

    public Double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(Double successRate) {
        this.successRate = successRate;
    }

    public Double getFailRate() {
        return failRate;
    }

    public void setFailRate(Double failRate) {
        this.failRate = failRate;
    }

    public Integer getAvgLatency() {
        return avgLatency;
    }

    public void setAvgLatency(Integer avgLatency) {
        this.avgLatency = avgLatency;
    }

    public Integer getP50Latency() {
        return p50Latency;
    }

    public void setP50Latency(Integer p50Latency) {
        this.p50Latency = p50Latency;
    }

    public Integer getP95Latency() {
        return p95Latency;
    }

    public void setP95Latency(Integer p95Latency) {
        this.p95Latency = p95Latency;
    }

    public Integer getP99Latency() {
        return p99Latency;
    }

    public void setP99Latency(Integer p99Latency) {
        this.p99Latency = p99Latency;
    }

    public Integer getMaxLatency() {
        return maxLatency;
    }

    public void setMaxLatency(Integer maxLatency) {
        this.maxLatency = maxLatency;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }
}
