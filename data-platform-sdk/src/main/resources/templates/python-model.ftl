"""
${modelName} dataclass (auto-generated).
"""
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class ${modelName}:
    """${modelName} model."""
<#list fields as f>
    ${f.name}: <#if f.type == "String">Optional[str]<#elseif f.type == "int" || f.type == "Integer">Optional[int]<#elseif f.type == "boolean" || f.type == "Boolean">Optional[bool]<#elseif f.type == "List<String>">List[str]<#elseif f.type == "Map<String,Object>">Dict[str, Any]<#else>Optional[Any]</#if> = <#if f.type == "List<String>">field(default_factory=list)<#elseif f.type == "Map<String,Object>">field(default_factory=dict)<#else>None</#if>
</#list>

    def to_dict(self) -> Dict[str, Any]:
        """Serialize to dict."""
        return {
<#list fields as f>
            "${f.name}": self.${f.name},
</#list>
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "${modelName}":
        """Deserialize from dict."""
        return cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})
