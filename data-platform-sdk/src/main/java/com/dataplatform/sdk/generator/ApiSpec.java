package com.dataplatform.sdk.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * API 规格描述，用于代码生成器驱动模板渲染。
 */
public class ApiSpec {

    private String serviceName;
    private String baseUrl;
    private String version;
    private List<Endpoint> endpoints = new ArrayList<>();

    public ApiSpec() {
    }

    public ApiSpec(String serviceName, String baseUrl, String version) {
        this.serviceName = serviceName;
        this.baseUrl = baseUrl;
        this.version = version;
    }

    /** 返回平台默认 API 规格（硬编码）。 */
    public static ApiSpec fromDefaults(String baseUrl) {
        ApiSpec spec = new ApiSpec("DataPlatform", baseUrl, "v1");

        // Vendor CRUD
        spec.addEndpoint("GET", "/api/vendor/list", "listVendors",
                List.of(new Parameter("page", "int", false), new Parameter("size", "int", false)),
                "PageResult");
        spec.addEndpoint("GET", "/api/vendor/{id}", "getVendor",
                List.of(new Parameter("id", "String", true)),
                "Vendor");
        spec.addEndpoint("POST", "/api/vendor/create", "createVendor",
                List.of(new Parameter("body", "Vendor", true)),
                "Vendor");
        spec.addEndpoint("PUT", "/api/vendor/update", "updateVendor",
                List.of(new Parameter("body", "Vendor", true)),
                "Vendor");
        spec.addEndpoint("DELETE", "/api/vendor/{id}", "deleteVendor",
                List.of(new Parameter("id", "String", true)),
                "Boolean");

        // Caller CRUD
        spec.addEndpoint("GET", "/api/caller/list", "listCallers",
                List.of(new Parameter("page", "int", false), new Parameter("size", "int", false)),
                "PageResult");
        spec.addEndpoint("GET", "/api/caller/{id}", "getCaller",
                List.of(new Parameter("id", "String", true)),
                "Caller");
        spec.addEndpoint("POST", "/api/caller/create", "createCaller",
                List.of(new Parameter("body", "Caller", true)),
                "Caller");
        spec.addEndpoint("PUT", "/api/caller/update", "updateCaller",
                List.of(new Parameter("body", "Caller", true)),
                "Caller");
        spec.addEndpoint("DELETE", "/api/caller/{id}", "deleteCaller",
                List.of(new Parameter("id", "String", true)),
                "Boolean");

        // Billing
        spec.addEndpoint("GET", "/api/billing/query", "queryBilling",
                List.of(new Parameter("vendorId", "String", false), new Parameter("month", "String", false)),
                "BillingResult");
        spec.addEndpoint("GET", "/api/billing/summary", "billingSummary",
                List.of(new Parameter("year", "String", false)),
                "BillingSummary");

        // Access / Call
        spec.addEndpoint("POST", "/api/call/query", "queryCall",
                List.of(new Parameter("body", "CallRequest", true)),
                "CallResult");
        spec.addEndpoint("POST", "/api/call/batch", "batchQueryCall",
                List.of(new Parameter("body", "BatchCallRequest", true)),
                "BatchCallResult");

        return spec;
    }

    public void addEndpoint(String method, String path, String operationId,
                            List<Parameter> parameters, String responseType) {
        this.endpoints.add(new Endpoint(method, path, operationId, parameters, responseType));
    }

    // --- getters / setters ---

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public List<Endpoint> getEndpoints() { return endpoints; }
    public void setEndpoints(List<Endpoint> endpoints) { this.endpoints = endpoints; }

    // --- inner classes ---

    public static class Endpoint {
        private String method;
        private String path;
        private String operationId;
        private List<Parameter> parameters = new ArrayList<>();
        private String responseType;

        public Endpoint() {
        }

        public Endpoint(String method, String path, String operationId,
                        List<Parameter> parameters, String responseType) {
            this.method = method;
            this.path = path;
            this.operationId = operationId;
            this.parameters = parameters;
            this.responseType = responseType;
        }

        /** 是否包含路径参数 {xxx}。 */
        public boolean isHasPathParams() {
            return path.contains("{");
        }

        /** 获取路径中 {xxx} 参数名列表。 */
        public List<String> getPathParamNames() {
            List<String> names = new ArrayList<>();
            String p = path;
            while (p.contains("{")) {
                int start = p.indexOf('{');
                int end = p.indexOf('}');
                names.add(p.substring(start + 1, end));
                p = p.substring(end + 1);
            }
            return names;
        }

        /** 非路径参数、非 body 的 query 参数。 */
        public List<Parameter> getQueryParams() {
            List<String> pathNames = getPathParamNames();
            List<Parameter> result = new ArrayList<>();
            for (Parameter param : parameters) {
                if (!pathNames.contains(param.getName()) && !"body".equals(param.getName())) {
                    result.add(param);
                }
            }
            return result;
        }

        /** 是否有 request body 参数。 */
        public boolean isHasBody() {
            return parameters.stream().anyMatch(p -> "body".equals(p.getName()));
        }

        /** 获取 body 参数。 */
        public Parameter getBodyParam() {
            return parameters.stream().filter(p -> "body".equals(p.getName()))
                    .findFirst().orElse(null);
        }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getOperationId() { return operationId; }
        public void setOperationId(String operationId) { this.operationId = operationId; }

        public List<Parameter> getParameters() { return parameters; }
        public void setParameters(List<Parameter> parameters) { this.parameters = parameters; }

        public String getResponseType() { return responseType; }
        public void setResponseType(String responseType) { this.responseType = responseType; }
    }

    public static class Parameter {
        private String name;
        private String type;
        private boolean required;

        public Parameter() {
        }

        public Parameter(String name, String type, boolean required) {
            this.name = name;
            this.type = type;
            this.required = required;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
    }
}
