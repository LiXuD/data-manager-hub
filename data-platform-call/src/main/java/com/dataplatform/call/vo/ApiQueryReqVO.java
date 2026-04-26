package com.dataplatform.call.vo;

import java.util.Map;

public class ApiQueryReqVO {
    private String vendorCode;
    private String dataType;
    private String interfaceCode;
    private Map<String, Object> params;
    private QueryOptions options;

    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getInterfaceCode() { return interfaceCode; }
    public void setInterfaceCode(String interfaceCode) { this.interfaceCode = interfaceCode; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
    public QueryOptions getOptions() { return options; }
    public void setOptions(QueryOptions options) { this.options = options; }

    public static class QueryOptions {
        private Integer timeout;
        private Boolean fallback;

        public Integer getTimeout() { return timeout; }
        public void setTimeout(Integer timeout) { this.timeout = timeout; }
        public Boolean getFallback() { return fallback; }
        public void setFallback(Boolean fallback) { this.fallback = fallback; }
    }
}