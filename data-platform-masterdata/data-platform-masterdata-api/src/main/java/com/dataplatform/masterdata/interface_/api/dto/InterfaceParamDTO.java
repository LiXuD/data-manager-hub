package com.dataplatform.masterdata.interface_.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 主数据域接口定义的 Interface Param DTO。
 * <p>跨服务契约数据对象，用于 api 模块暴露远程接口时传递稳定字段。</p>
 */
public class InterfaceParamDTO implements Serializable {
    private Long id;
    private Long interfaceId;
    private String direction;
    private Long parentId;
    private String paramName;
    private String description;
    private String paramType;
    private String arrayItemType;
    private Boolean required;
    private String defaultValue;
    private String exampleValue;
    private String constraintConfig;
    private Integer sort;
    private List<InterfaceParamDTO> children = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInterfaceId() { return interfaceId; }
    public void setInterfaceId(Long interfaceId) { this.interfaceId = interfaceId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getParamName() { return paramName; }
    public void setParamName(String paramName) { this.paramName = paramName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getParamType() { return paramType; }
    public void setParamType(String paramType) { this.paramType = paramType; }
    public String getArrayItemType() { return arrayItemType; }
    public void setArrayItemType(String arrayItemType) { this.arrayItemType = arrayItemType; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public String getExampleValue() { return exampleValue; }
    public void setExampleValue(String exampleValue) { this.exampleValue = exampleValue; }
    public String getConstraintConfig() { return constraintConfig; }
    public void setConstraintConfig(String constraintConfig) { this.constraintConfig = constraintConfig; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public List<InterfaceParamDTO> getChildren() { return children; }
    public void setChildren(List<InterfaceParamDTO> children) {
        this.children = children != null ? children : new ArrayList<>();
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
