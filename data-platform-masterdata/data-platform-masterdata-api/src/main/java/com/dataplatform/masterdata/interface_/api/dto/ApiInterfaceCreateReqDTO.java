package com.dataplatform.masterdata.interface_.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/**
 * 主数据域接口定义的 Api Interface Create Req DTO。
 * <p>跨服务契约数据对象，用于 api 模块暴露远程接口时传递稳定字段。</p>
 */
public class ApiInterfaceCreateReqDTO implements Serializable {

    @NotBlank(message = "接口编码不能为空")
    private String interfaceCode;

    @NotBlank(message = "接口名称不能为空")
    private String interfaceName;

    private Long dataTypeId;
    private Long vendorId;
    private String path;
    private String description;
    private String requestSchema;
    private String responseSchema;
    private Integer sort;
    private String status;

    public String getInterfaceCode() { return interfaceCode; }
    public void setInterfaceCode(String interfaceCode) { this.interfaceCode = interfaceCode; }
    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
    public Long getDataTypeId() { return dataTypeId; }
    public void setDataTypeId(Long dataTypeId) { this.dataTypeId = dataTypeId; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRequestSchema() { return requestSchema; }
    public void setRequestSchema(String requestSchema) { this.requestSchema = requestSchema; }
    public String getResponseSchema() { return responseSchema; }
    public void setResponseSchema(String responseSchema) { this.responseSchema = responseSchema; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
