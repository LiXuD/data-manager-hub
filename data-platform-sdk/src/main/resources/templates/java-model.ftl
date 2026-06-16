package com.dataplatform.client.model;

import java.util.List;
import java.util.Map;

/**
 * ${modelName} (auto-generated).
 */
public class ${modelName} {

<#list fields as field>
    private ${field.type} ${field.name};
</#list>

    public ${modelName}() {
    }

<#list fields as field>
    public ${field.type} get${field.name?cap_first}() {
        return ${field.name};
    }

    public void set${field.name?cap_first}(${field.type} ${field.name}) {
        this.${field.name} = ${field.name};
    }

</#list>
    @Override
    public String toString() {
        return "${modelName}{"
<#list fields as field>
                + "${field.name}=" + ${field.name}<#if field_has_next> + ", "</#if>
</#list>
                + "}";
    }
}
