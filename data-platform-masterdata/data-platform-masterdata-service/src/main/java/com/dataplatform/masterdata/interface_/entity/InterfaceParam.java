package com.dataplatform.masterdata.interface_.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dataplatform.common.handler.JsonbTypeHandler;
import java.time.LocalDateTime;

/**
 * 接口参数定义实体
 * 挂在接口级，定义该接口有哪些请求参数，所有厂商共用。
 */
@TableName(value = "interface_param", autoResultMap = true)
public class InterfaceParam {

    @TableId(type = IdType.AUTO)
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

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String constraintConfig;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
