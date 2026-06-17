package com.dataplatform.access.call.vo;

import java.util.Map;

/**
 * 访问域数据调用的 Open Api Query Req VO。
 * <p>Web 层请求或响应视图对象，用于隔离页面接口与数据库实体。</p>
 */
public class OpenApiQueryReqVO {

    private String requestId;
    private String apiCode;
    private String apiVersion;
    private String productCode;
    private String sceneCode;
    private Boolean useCache;
    private Integer cacheDays;
    private Map<String, Object> params;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getApiCode() {
        return apiCode;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public void setSceneCode(String sceneCode) {
        this.sceneCode = sceneCode;
    }

    public Boolean getUseCache() {
        return useCache;
    }

    public void setUseCache(Boolean useCache) {
        this.useCache = useCache;
    }

    public Integer getCacheDays() {
        return cacheDays;
    }

    public void setCacheDays(Integer cacheDays) {
        this.cacheDays = cacheDays;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
