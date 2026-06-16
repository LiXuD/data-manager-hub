// ${modelName} struct (auto-generated).
package dataplatform

// ${modelName} represents the ${modelName} model.
type ${modelName} struct {
<#list fields as f>
	${f.name?cap_first} <#if f.type == "String">string<#elseif f.type == "int" || f.type == "Integer">int<#elseif f.type == "boolean" || f.type == "Boolean">bool<#elseif f.type == "List<String>">[]string<#elseif f.type == "Map<String,Object>">map[string]interface{}<#else>interface{}</#if> `json:"${f.name}"`
</#list>
}
