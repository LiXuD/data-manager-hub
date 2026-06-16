"""
${serviceName} Python SDK Client (auto-generated).
API version: ${version}
"""
import json
import urllib.parse
from typing import Any, Dict, Optional


class ${serviceName}Client:
    """${serviceName} API client."""

    def __init__(self, base_url: str, api_key: str):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self._headers = {
            "Content-Type": "application/json",
            "X-API-Key": api_key,
        }

    def _request(self, method: str, path: str, body: Optional[str] = None,
                 params: Optional[Dict[str, Any]] = None) -> str:
        import urllib.request
        if params:
            query = urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
            if query:
                path = f"{path}?{query}"
        url = f"{self.base_url}{path}"
        data = body.encode("utf-8") if body else None
        req = urllib.request.Request(url, data=data, headers=self._headers, method=method)
        with urllib.request.urlopen(req) as resp:
            result = resp.read().decode("utf-8")
            return result

<#list endpoints as ep>
    def ${snake_case(ep.operationId)}(<#list ep.parameters as param>${param.name}: Optional[str] = None<#if param_has_next>, </#if></#list>) -> str:
        """
        ${ep.operationId} - ${ep.method} ${ep.path}
        """
<#if ep.isHasPathParams()>
        path = "${ep.path}"
<#list ep.pathParamNames as ppn>
        path = path.replace("{${ppn}}", str(${ppn}))
</#list>
<#else>
        path = "${ep.path}"
</#if>
<#assign queryParams = ep.queryParams>
<#if queryParams?has_content>
        params = {
<#list queryParams as qp>
            "${qp.name}": ${qp.name},
</#list>
        }
<#else>
        params = None
</#if>
<#if ep.isHasBody()>
        body = ${ep.bodyParam.name}
<#else>
        body = None
</#if>
        return self._request("${ep.method}", path, body=body, params=params)

</#list>
    # --- Low-level helpers ---

    def get(self, path: str, params: Optional[Dict[str, Any]] = None) -> str:
        """Generic GET request."""
        return self._request("GET", path, params=params)

    def post(self, path: str, body: Optional[str] = None) -> str:
        """Generic POST request."""
        return self._request("POST", path, body=body)
