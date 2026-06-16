// Package dataplatform - ${serviceName} Go SDK Client (auto-generated).
// API version: ${version}
package dataplatform

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
)

// Client represents the ${serviceName} API client.
type Client struct {
	BaseURL    string
	APIKey     string
	HTTPClient *http.Client
}

// NewClient creates a new ${serviceName} client.
func NewClient(baseURL, apiKey string) *Client {
	return &Client{
		BaseURL:    strings.TrimRight(baseURL, "/"),
		APIKey:     apiKey,
		HTTPClient: &http.Client{},
	}
}

func (c *Client) doRequest(method, path string, body interface{}) ([]byte, error) {
	var bodyReader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("marshal body: %w", err)
		}
		bodyReader = bytes.NewReader(data)
	}

	req, err := http.NewRequest(method, c.BaseURL+path, bodyReader)
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-API-Key", c.APIKey)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("execute request: %w", err)
	}
	defer resp.Body.Close()

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read response: %w", err)
	}
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("api error %d: %s", resp.StatusCode, string(data))
	}
	return data, nil
}

<#list endpoints as ep>
// ${ep.operationId?cap_first} - ${ep.method} ${ep.path}
func (c *Client) ${ep.operationId?cap_first}(<#list ep.parameters as param>${param.name} <#if param.type == "int">int<#elseif param.type == "String" || param.type == "string">string<#else>interface{}</#if><#if param_has_next>, </#if></#list>) ([]byte, error) {
<#if ep.isHasPathParams()>
	path := "${ep.path}"
<#list ep.pathParamNames as ppn>
	path = strings.Replace(path, "{${ppn}}", fmt.Sprintf("%v", ${ppn}), 1)
</#list>
<#else>
	path := "${ep.path}"
</#if>
<#assign queryParams = ep.queryParams>
<#if queryParams?has_content>
	params := url.Values{}
<#list queryParams as qp>
	if ${qp.name} != <#if qp.type == "int">0<#else>""</#if> {
		params.Set("${qp.name}", fmt.Sprintf("%v", ${qp.name}))
	}
</#list>
	if len(params) > 0 {
		path = path + "?" + params.Encode()
	}
</#if>
<#if ep.method == "GET">
	return c.doRequest(http.MethodGet, path, nil)
<#elseif ep.method == "DELETE">
	return c.doRequest(http.MethodDelete, path, nil)
<#elseif ep.method == "POST">
<#if ep.isHasBody()>
	return c.doRequest(http.MethodPost, path, ${ep.bodyParam.name})
<#else>
	return c.doRequest(http.MethodPost, path, nil)
</#if>
<#elseif ep.method == "PUT">
<#if ep.isHasBody()>
	return c.doRequest(http.MethodPut, path, ${ep.bodyParam.name})
<#else>
	return c.doRequest(http.MethodPut, path, nil)
</#if>
<#else>
	return c.doRequest("${ep.method}", path, <#if ep.isHasBody()>${ep.bodyParam.name}<#else>nil</#if>)
</#if>
}

</#list>
// --- Low-level helpers ---

// Get performs a generic GET request.
func (c *Client) Get(path string) ([]byte, error) {
	return c.doRequest(http.MethodGet, path, nil)
}

// Post performs a generic POST request.
func (c *Client) Post(path string, body interface{}) ([]byte, error) {
	return c.doRequest(http.MethodPost, path, body)
}
