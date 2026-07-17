package com.dataplatform.masterdata.interface_.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 接口调用契约。请求字段描述 params，响应字段描述响应 data。
 */
public class InterfaceContractDTO implements Serializable {
    private Long interfaceId;
    private String interfaceCode;
    private String interfaceName;
    private String description;
    private String requestSchema;
    private String responseSchema;
    private List<InterfaceParamDTO> requestFields = new ArrayList<>();
    private List<InterfaceParamDTO> responseFields = new ArrayList<>();
    private LocalDateTime updatedAt;

    public Long getInterfaceId() { return interfaceId; }
    public void setInterfaceId(Long interfaceId) { this.interfaceId = interfaceId; }
    public String getInterfaceCode() { return interfaceCode; }
    public void setInterfaceCode(String interfaceCode) { this.interfaceCode = interfaceCode; }
    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRequestSchema() { return requestSchema; }
    public void setRequestSchema(String requestSchema) { this.requestSchema = requestSchema; }
    public String getResponseSchema() { return responseSchema; }
    public void setResponseSchema(String responseSchema) { this.responseSchema = responseSchema; }
    public List<InterfaceParamDTO> getRequestFields() { return requestFields; }
    public void setRequestFields(List<InterfaceParamDTO> requestFields) {
        this.requestFields = requestFields != null ? requestFields : new ArrayList<>();
    }
    public List<InterfaceParamDTO> getResponseFields() { return responseFields; }
    public void setResponseFields(List<InterfaceParamDTO> responseFields) {
        this.responseFields = responseFields != null ? responseFields : new ArrayList<>();
    }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
