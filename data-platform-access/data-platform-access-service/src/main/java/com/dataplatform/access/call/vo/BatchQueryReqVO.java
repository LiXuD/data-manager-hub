package com.dataplatform.access.call.vo;

import java.util.List;
import java.util.Map;

public class BatchQueryReqVO {
    private String vendorCode;
    private String dataType;
    private String interfaceCode;
    private List<Map<String, Object>> paramsList;
    private BatchOptions options;

    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getInterfaceCode() { return interfaceCode; }
    public void setInterfaceCode(String interfaceCode) { this.interfaceCode = interfaceCode; }
    public List<Map<String, Object>> getParamsList() { return paramsList; }
    public void setParamsList(List<Map<String, Object>> paramsList) { this.paramsList = paramsList; }
    public BatchOptions getOptions() { return options; }
    public void setOptions(BatchOptions options) { this.options = options; }

    public static class BatchOptions {
        private Integer timeout;
        private Integer maxBatchSize;

        public Integer getTimeout() { return timeout; }
        public void setTimeout(Integer timeout) { this.timeout = timeout; }
        public Integer getMaxBatchSize() { return maxBatchSize; }
        public void setMaxBatchSize(Integer maxBatchSize) { this.maxBatchSize = maxBatchSize; }
    }
}