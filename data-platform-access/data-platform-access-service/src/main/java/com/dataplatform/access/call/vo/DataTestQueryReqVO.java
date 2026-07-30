package com.dataplatform.access.call.vo;

/**
 * 登录态数据查询测试请求。API Key 只传资源 ID，明文密钥不返回浏览器。
 */
public class DataTestQueryReqVO extends OpenApiQueryReqVO {

    private Long apiKeyId;

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public void setApiKeyId(Long apiKeyId) {
        this.apiKeyId = apiKeyId;
    }
}
