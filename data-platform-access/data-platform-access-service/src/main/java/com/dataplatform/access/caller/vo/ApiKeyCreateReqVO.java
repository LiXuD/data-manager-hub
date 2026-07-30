package com.dataplatform.access.caller.vo;

import java.util.List;

/**
 * 创建 API Key 请求。
 */
public class ApiKeyCreateReqVO {

    private Long callerId;
    private String name;
    private List<Long> productIds;

    public Long getCallerId() {
        return callerId;
    }

    public void setCallerId(Long callerId) {
        this.callerId = callerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Long> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<Long> productIds) {
        this.productIds = productIds;
    }
}
