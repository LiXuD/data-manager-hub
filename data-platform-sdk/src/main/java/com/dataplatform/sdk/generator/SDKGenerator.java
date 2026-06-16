package com.dataplatform.sdk.generator;

import freemarker.template.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SDK 代码生成器 — 基于 Freemarker 模板驱动。
 */
public class SDKGenerator {

    private static final List<String> MODEL_NAMES = List.of(
            "Vendor", "Caller", "CallRequest", "BatchCallRequest",
            "CallResult", "BatchCallResult", "BillingResult", "BillingSummary", "PageResult");

    private final Configuration fmConfig;

    public SDKGenerator() {
        this.fmConfig = new Configuration(Configuration.VERSION_2_3_33);
        fmConfig.setClassLoaderForTemplateLoading(
                getClass().getClassLoader(), "templates");
        fmConfig.setDefaultEncoding("UTF-8");
        fmConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    /** 仅用于测试注入。 */
    SDKGenerator(Configuration fmConfig) {
        this.fmConfig = fmConfig;
    }

    // ======================== Spec-based generation ========================

    /** 生成 Java 客户端，返回 fileName → content 映射。 */
    public Map<String, String> generateJavaClient(ApiSpec spec) throws IOException {
        requireSpec(spec);
        Map<String, String> files = new LinkedHashMap<>();
        Map<String, Object> model = specModel(spec);

        files.put(spec.getServiceName() + "Client.java",
                render("java-client.ftl", model));

        for (String modelName : MODEL_NAMES) {
            files.put(modelName + ".java",
                    render("java-model.ftl", modelFor(modelName)));
        }
        return files;
    }

    /** 生成 Python 客户端。 */
    public Map<String, String> generatePythonClient(ApiSpec spec) throws IOException {
        requireSpec(spec);
        Map<String, String> files = new LinkedHashMap<>();
        Map<String, Object> model = specModel(spec);

        files.put(snakeCase(spec.getServiceName()) + "_client.py",
                render("python-client.ftl", model));

        for (String modelName : MODEL_NAMES) {
            files.put(snakeCase(modelName) + ".py",
                    render("python-model.ftl", modelFor(modelName)));
        }
        return files;
    }

    /** 生成 Go 客户端。 */
    public Map<String, String> generateGoClient(ApiSpec spec) throws IOException {
        requireSpec(spec);
        Map<String, String> files = new LinkedHashMap<>();
        Map<String, Object> model = specModel(spec);

        files.put("client.go",
                render("go-client.ftl", model));

        for (String modelName : MODEL_NAMES) {
            files.put(snakeCase(modelName) + ".go",
                    render("go-model.ftl", modelFor(modelName)));
        }
        return files;
    }

    /** 生成全部语言。 */
    public Map<String, Map<String, String>> generateAllClients(ApiSpec spec) throws IOException {
        Map<String, Map<String, String>> all = new LinkedHashMap<>();
        all.put("java", generateJavaClient(spec));
        all.put("python", generatePythonClient(spec));
        all.put("go", generateGoClient(spec));
        return all;
    }

    // ======================== Backward-compatible shortcuts ========================

    /** 旧接口 — 内部构造默认 ApiSpec 后返回单字符串。 */
    public String generateJavaSDK(String baseUrl) {
        requireBaseUrl(baseUrl);
        try {
            Map<String, String> files = generateJavaClient(ApiSpec.fromDefaults(baseUrl));
            return files.values().stream().reduce("", String::concat);
        } catch (IOException e) {
            throw new RuntimeException("Template rendering failed", e);
        }
    }

    public String generatePythonSDK(String baseUrl) {
        requireBaseUrl(baseUrl);
        try {
            Map<String, String> files = generatePythonClient(ApiSpec.fromDefaults(baseUrl));
            return files.values().stream().reduce("", String::concat);
        } catch (IOException e) {
            throw new RuntimeException("Template rendering failed", e);
        }
    }

    public String generateGoSDK(String baseUrl) {
        requireBaseUrl(baseUrl);
        try {
            Map<String, String> files = generateGoClient(ApiSpec.fromDefaults(baseUrl));
            return files.values().stream().reduce("", String::concat);
        } catch (IOException e) {
            throw new RuntimeException("Template rendering failed", e);
        }
    }

    public Map<String, String> generateAllSDKs(String baseUrl) {
        requireBaseUrl(baseUrl);
        Map<String, String> sdks = new HashMap<>();
        sdks.put("java", generateJavaSDK(baseUrl));
        sdks.put("python", generatePythonSDK(baseUrl));
        sdks.put("go", generateGoSDK(baseUrl));
        return sdks;
    }

    // ======================== Internals ========================

    private String render(String templateName, Map<String, Object> model) throws IOException {
        Template template = fmConfig.getTemplate(templateName);
        StringWriter writer = new StringWriter();
        try {
            template.process(model, writer);
        } catch (TemplateException e) {
            throw new IOException("Template rendering failed: " + templateName, e);
        }
        return writer.toString();
    }

    private Map<String, Object> specModel(ApiSpec spec) {
        Map<String, Object> model = new HashMap<>();
        model.put("serviceName", spec.getServiceName());
        model.put("baseUrl", spec.getBaseUrl());
        model.put("version", spec.getVersion());
        model.put("endpoints", spec.getEndpoints());
        // Expose snake_case conversion to templates
        model.put("snake_case", (TemplateMethodModelEx) arguments -> {
            String input = arguments.get(0).toString();
            return snakeCase(input);
        });
        return model;
    }

    private Map<String, Object> modelFor(String modelName) {
        Map<String, Object> model = new HashMap<>();
        model.put("modelName", modelName);
        model.put("fields", modelFields(modelName));
        return model;
    }

    private java.util.List<ApiSpec.Parameter> modelFields(String modelName) {
        return switch (modelName) {
            case "Vendor" -> java.util.List.of(
                    new ApiSpec.Parameter("id", "String", false),
                    new ApiSpec.Parameter("name", "String", true),
                    new ApiSpec.Parameter("code", "String", true),
                    new ApiSpec.Parameter("contact", "String", false),
                    new ApiSpec.Parameter("status", "String", false));
            case "Caller" -> java.util.List.of(
                    new ApiSpec.Parameter("id", "String", false),
                    new ApiSpec.Parameter("name", "String", true),
                    new ApiSpec.Parameter("vendorId", "String", true),
                    new ApiSpec.Parameter("endpoint", "String", false),
                    new ApiSpec.Parameter("status", "String", false));
            case "CallRequest" -> java.util.List.of(
                    new ApiSpec.Parameter("dataType", "String", true),
                    new ApiSpec.Parameter("params", "Map<String,Object>", false));
            case "BatchCallRequest" -> java.util.List.of(
                    new ApiSpec.Parameter("dataType", "String", true),
                    new ApiSpec.Parameter("ids", "List<String>", true));
            case "CallResult" -> java.util.List.of(
                    new ApiSpec.Parameter("success", "boolean", false),
                    new ApiSpec.Parameter("data", "Map<String,Object>", false));
            case "BatchCallResult" -> java.util.List.of(
                    new ApiSpec.Parameter("success", "boolean", false),
                    new ApiSpec.Parameter("results", "List<String>", false));
            case "BillingResult" -> java.util.List.of(
                    new ApiSpec.Parameter("vendorId", "String", false),
                    new ApiSpec.Parameter("month", "String", false),
                    new ApiSpec.Parameter("totalAmount", "String", false));
            case "BillingSummary" -> java.util.List.of(
                    new ApiSpec.Parameter("year", "String", false),
                    new ApiSpec.Parameter("totalAmount", "String", false),
                    new ApiSpec.Parameter("details", "List<String>", false));
            case "PageResult" -> java.util.List.of(
                    new ApiSpec.Parameter("total", "int", false),
                    new ApiSpec.Parameter("page", "int", false),
                    new ApiSpec.Parameter("size", "int", false),
                    new ApiSpec.Parameter("data", "List<String>", false));
            default -> java.util.List.of();
        };
    }

    private String snakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    private void requireSpec(ApiSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("apiSpec must not be null");
        }
        requireBaseUrl(spec.getBaseUrl());
    }

    private void requireBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
    }
}
