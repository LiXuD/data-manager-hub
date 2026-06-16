package com.dataplatform.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.List;

/**
 * ${serviceName} Java SDK Client (auto-generated).
 * API version: ${version}
 */
public class ${serviceName}Client {

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public ${serviceName}Client(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    public ${serviceName}Client(String baseUrl, String apiKey, HttpClient httpClient) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.httpClient = httpClient;
    }

<#list endpoints as ep>
    /**
     * ${ep.operationId} - ${ep.method} ${ep.path}
     */
    public String ${ep.operationId}(<#list ep.parameters as param>String ${param.name}<#if param_has_next>, </#if></#list>) throws IOException, InterruptedException {
<#if ep.isHasPathParams()>
        String path = "${ep.path}";
<#list ep.pathParamNames as ppn>
        path = path.replace("{${ppn}}", String.valueOf(${ppn}));
</#list>
<#else>
        String path = "${ep.path}";
</#if>
<#assign queryParams = ep.queryParams>
<#if queryParams?has_content>
        StringBuilder query = new StringBuilder();
<#list queryParams as qp>
        if (${qp.name} != null) {
            if (query.length() > 0) query.append("&");
            query.append("${qp.name}=").append(java.net.URLEncoder.encode(${qp.name}, java.nio.charset.StandardCharsets.UTF_8));
        }
</#list>
        if (query.length() > 0) {
            path = path + "?" + query;
        }
</#if>

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey);

<#if ep.method == "GET">
        builder.GET();
<#elseif ep.method == "DELETE">
        builder.DELETE();
<#else>
<#if ep.isHasBody()>
        String body = ${ep.bodyParam.name} != null ? ${ep.bodyParam.name} : "{}";
<#else>
        String body = "{}";
</#if>
        builder.method("${ep.method}", HttpRequest.BodyPublishers.ofString(body));
</#if>

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("API error " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

</#list>
    // --- Low-level helpers ---

    public String get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("X-API-Key", apiKey)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("API error " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    public String post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("API error " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }
}
