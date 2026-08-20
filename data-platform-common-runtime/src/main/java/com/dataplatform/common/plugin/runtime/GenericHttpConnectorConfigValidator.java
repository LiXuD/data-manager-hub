package com.dataplatform.common.plugin.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Platform-owned conditional validator for the fixed generic-http configuration contract. */
public final class GenericHttpConnectorConfigValidator {

    public static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    public static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";
    public static final long MAX_RESPONSE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_HEADERS = 64;
    private static final int MAX_MAPPINGS = 256;
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}");
    private static final Pattern PATH_SEGMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]{0,127}");
    private static final Set<String> FORBIDDEN_PATH_SEGMENTS = Set.of(
            "__proto__", "prototype", "constructor");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "endpoint", "method", "contentType", "headers", "requestMapping", "auth",
            "successHttpStatuses", "businessCodePath", "successBusinessCodes", "dataPath");
    private static final Set<String> HEADER_FIELDS = Set.of("name", "value");
    private static final Set<String> MAPPING_FIELDS = Set.of(
            "sourceField", "targetField", "required", "defaultValue", "transformType");
    private static final Set<String> AUTH_FIELDS = Set.of(
            "type", "tokenRef", "usernameRef", "passwordRef", "keyName", "keyRef", "location");
    private static final Set<String> FORBIDDEN_HEADERS = Set.of(
            "host", "content-length", "connection", "upgrade", "transfer-encoding", "trailer",
            "proxy-connection", "keep-alive", "te", "authorization", "proxy-authorization",
            "cookie", "content-type");
    private static final Set<String> TRANSFORMS = Set.of("none", "trim", "uppercase", "lowercase");

    private GenericHttpConnectorConfigValidator() {
    }

    public static ValidatedConfig validate(JsonNode config, Predicate<String> secretOwned) {
        if (config == null || !config.isObject()) {
            throw invalid("Generic HTTP config must be an object");
        }
        if (config.toString().getBytes(StandardCharsets.UTF_8).length
                > PipelineCompiler.MAX_STAGE_CONFIG_BYTES) {
            throw invalid("Generic HTTP config exceeds the runtime stage limit");
        }
        rejectUnknown(config, ROOT_FIELDS);
        URI endpoint = endpoint(fieldText(config, "endpoint", 2048));
        String method = fieldText(config, "method", 16).toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) throw invalid("Generic HTTP method is unsupported");
        String contentType = contentType(config.get("contentType"));
        List<Header> headers = headers(config.get("headers"));
        List<RequestMapping> mappings = mappings(config.get("requestMapping"));
        Auth auth = auth(config.get("auth"), secretOwned == null ? ignored -> false : secretOwned);
        Set<Integer> statuses = statuses(config.get("successHttpStatuses"));
        String businessPath = optionalText(config.get("businessCodePath"), 256);
        if (businessPath != null) validatePath(businessPath, true);
        List<String> successCodes = stringList(config.get("successBusinessCodes"), 128, 128);
        if ((businessPath == null) != (successCodes == null)) {
            throw invalid("Business code path and success codes must be configured together");
        }
        if (successCodes != null && successCodes.isEmpty()) {
            throw invalid("Business success codes cannot be empty");
        }
        String dataPath = optionalText(config.get("dataPath"), 256);
        if (dataPath != null) validatePath(dataPath, true);
        return new ValidatedConfig(endpoint, method, contentType, headers, mappings, auth,
                statuses, businessPath, successCodes == null ? List.of() : successCodes, dataPath);
    }

    private static URI endpoint(String value) {
        try {
            if (containsControl(value) || containsEncodedControl(value)) {
                throw invalid("Generic HTTP endpoint is invalid");
            }
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getRawQuery() != null) {
                throw invalid("Generic HTTP endpoint must be an absolute HTTPS URL");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Generic HTTP")) {
                throw exception;
            }
            throw invalid("Generic HTTP endpoint is invalid");
        }
    }

    private static String contentType(JsonNode node) {
        if (node == null || node.isNull()) return JSON_CONTENT_TYPE;
        String value = requiredText(node, "contentType", 128).toLowerCase(Locale.ROOT);
        if (value.equals("application/json") || value.equals(JSON_CONTENT_TYPE)) return JSON_CONTENT_TYPE;
        if (value.equals(FORM_CONTENT_TYPE)) return FORM_CONTENT_TYPE;
        throw invalid("Generic HTTP content type is unsupported");
    }

    private static List<Header> headers(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        if (!node.isArray() || node.size() > MAX_HEADERS) throw invalid("Generic HTTP headers are invalid");
        List<Header> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode item : node) {
            if (!item.isObject()) throw invalid("Generic HTTP header is invalid");
            rejectUnknown(item, HEADER_FIELDS);
            String name = fieldText(item, "name", 128);
            String normalized = name.toLowerCase(Locale.ROOT);
            String value = fieldText(item, "value", 4096);
            if (!HEADER_NAME.matcher(name).matches() || FORBIDDEN_HEADERS.contains(normalized)
                    || sensitiveHeader(normalized) || containsControl(value) || !seen.add(normalized)) {
                throw invalid("Generic HTTP header is invalid");
            }
            result.add(new Header(name, value));
        }
        return List.copyOf(result);
    }

    private static List<RequestMapping> mappings(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        if (!node.isArray() || node.size() > MAX_MAPPINGS) throw invalid("Generic HTTP request mapping is invalid");
        List<RequestMapping> result = new ArrayList<>();
        Set<String> targets = new HashSet<>();
        for (JsonNode item : node) {
            if (!item.isObject()) throw invalid("Generic HTTP request mapping is invalid");
            rejectUnknown(item, MAPPING_FIELDS);
            String source = fieldText(item, "sourceField", 256);
            String target = fieldText(item, "targetField", 256);
            validatePath(source, false);
            validatePath(target, false);
            boolean required = booleanValue(item.get("required"), false);
            JsonNode defaultValue = item.get("defaultValue");
            if (defaultValue != null && !defaultValue.isNull()) validateJson(defaultValue, 0, new int[] {0});
            String transform = optionalText(item.get("transformType"), 32);
            transform = transform == null ? "none" : transform;
            if (!TRANSFORMS.contains(transform) || targets.stream().anyMatch(
                    existing -> overlaps(existing, target)) || !targets.add(target)) {
                throw invalid("Generic HTTP request mapping is invalid");
            }
            result.add(new RequestMapping(source, target, required,
                    defaultValue == null ? null : defaultValue.deepCopy(), transform));
        }
        return List.copyOf(result);
    }

    private static Auth auth(JsonNode node, Predicate<String> secretOwned) {
        if (node == null || !node.isObject()) throw invalid("Generic HTTP auth is required");
        rejectUnknown(node, AUTH_FIELDS);
        String type = fieldText(node, "type", 32).toUpperCase(Locale.ROOT);
        Set<String> allowed;
        List<String> refs = new ArrayList<>();
        String keyName = null;
        String location = null;
        switch (type) {
            case "NONE" -> allowed = Set.of("type");
            case "BEARER" -> {
                allowed = Set.of("type", "tokenRef");
                refs.add(secret(node, "tokenRef", secretOwned));
            }
            case "BASIC" -> {
                allowed = Set.of("type", "usernameRef", "passwordRef");
                refs.add(secret(node, "usernameRef", secretOwned));
                refs.add(secret(node, "passwordRef", secretOwned));
            }
            case "API_KEY" -> {
                allowed = Set.of("type", "keyName", "keyRef", "location");
                keyName = fieldText(node, "keyName", 128);
                location = fieldText(node, "location", 16).toLowerCase(Locale.ROOT);
                if (!Set.of("header", "query").contains(location)
                        || containsControl(keyName) || !HEADER_NAME.matcher(keyName).matches()
                        || "header".equals(location) && FORBIDDEN_HEADERS.contains(keyName.toLowerCase(Locale.ROOT))) {
                    throw invalid("Generic HTTP API key placement is invalid");
                }
                refs.add(secret(node, "keyRef", secretOwned));
            }
            default -> throw invalid("Generic HTTP auth type is unsupported");
        }
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw invalid("Generic HTTP auth contains incompatible fields");
        });
        return new Auth(type, refs, keyName, location);
    }

    private static String secret(JsonNode node, String field, Predicate<String> owned) {
        String ref = fieldText(node, field, 256);
        boolean accepted;
        try { accepted = owned.test(ref); }
        catch (RuntimeException exception) { accepted = false; }
        if (!ref.equals(ref.trim()) || containsControl(ref) || !accepted) {
            throw invalid("Generic HTTP secret reference is invalid");
        }
        return ref;
    }

    private static Set<Integer> statuses(JsonNode node) {
        if (node == null || node.isNull()) {
            LinkedHashSet<Integer> defaults = new LinkedHashSet<>();
            for (int code = 200; code <= 299; code++) defaults.add(code);
            return Collections.unmodifiableSet(defaults);
        }
        if (!node.isArray() || node.isEmpty() || node.size() > 100) {
            throw invalid("Generic HTTP success statuses are invalid");
        }
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (JsonNode value : node) {
            if (!value.isIntegralNumber() || value.intValue() < 100 || value.intValue() > 599
                    || !result.add(value.intValue())) {
                throw invalid("Generic HTTP success statuses are invalid");
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static List<String> stringList(JsonNode node, int maxItems, int maxLength) {
        if (node == null || node.isNull()) return null;
        if (!node.isArray() || node.size() > maxItems) throw invalid("Generic HTTP string list is invalid");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > maxLength
                    || !item.asText().equals(item.asText().trim()) || containsControl(item.asText())
                    || !result.add(item.asText())) {
                throw invalid("Generic HTTP string list is invalid");
            }
        }
        return List.copyOf(result);
    }

    private static void rejectUnknown(JsonNode node, Set<String> allowed) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw invalid("Generic HTTP config contains an unknown field");
        });
    }

    private static String fieldText(JsonNode object, String field, int maxLength) {
        return requiredText(object.get(field), field, maxLength);
    }

    private static String requiredText(JsonNode node, String field, int maxLength) {
        if (node == null || !node.isTextual() || node.asText().isBlank()
                || node.asText().length() > maxLength || !node.asText().equals(node.asText().trim())
                || containsControl(node.asText())) {
            throw invalid("Generic HTTP " + field + " is invalid");
        }
        return node.asText();
    }

    private static String optionalText(JsonNode node, int maxLength) {
        if (node == null || node.isNull()) return null;
        return requiredText(node, "text", maxLength);
    }

    private static boolean booleanValue(JsonNode node, boolean fallback) {
        if (node == null || node.isNull()) return fallback;
        if (!node.isBoolean()) throw invalid("Generic HTTP boolean value is invalid");
        return node.booleanValue();
    }

    private static void validateJson(JsonNode node, int depth, int[] count) {
        if (depth > 16 || ++count[0] > 10_000 || node.isBinary() || node.isPojo()) {
            throw invalid("Generic HTTP structured value is too complex");
        }
        if (node.isObject()) node.fields().forEachRemaining(entry -> validateJson(entry.getValue(), depth + 1, count));
        else if (node.isArray()) node.forEach(item -> validateJson(item, depth + 1, count));
    }

    private static boolean containsControl(String value) {
        return value.chars().anyMatch(character -> character < 0x20 || character == 0x7f);
    }

    private static boolean containsEncodedControl(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (int index = 0; index + 2 < normalized.length(); index++) {
            if (normalized.charAt(index) != '%') continue;
            try {
                int decoded = Integer.parseInt(normalized.substring(index + 1, index + 3), 16);
                if (decoded < 0x20 || decoded == 0x7f) return true;
            } catch (NumberFormatException ignored) {
                // URI parsing below rejects malformed percent encodings.
            }
        }
        return false;
    }

    private static boolean sensitiveHeader(String normalizedName) {
        String compact = normalizedName.replaceAll("[^a-z0-9]", "");
        return compact.contains("authorization") || compact.contains("auth")
                || compact.contains("token") || compact.contains("secret")
                || compact.contains("apikey") || compact.contains("credential")
                || compact.contains("password") || compact.contains("signature");
    }

    private static void validatePath(String path, boolean allowIndexes) {
        String[] segments = path.split("\\.", -1);
        if (segments.length == 0 || segments.length > 32) throw invalid("Generic HTTP path is invalid");
        for (String segment : segments) {
            String normalized = segment.toLowerCase(Locale.ROOT);
            if (segment.isEmpty() || containsControl(segment)
                    || FORBIDDEN_PATH_SEGMENTS.contains(normalized)
                    || !(PATH_SEGMENT.matcher(segment).matches()
                    || allowIndexes && segment.matches("0|[1-9][0-9]{0,5}"))) {
                throw invalid("Generic HTTP path is invalid");
            }
        }
    }

    private static boolean overlaps(String left, String right) {
        return left.equals(right) || left.startsWith(right + ".") || right.startsWith(left + ".");
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    public record Header(String name, String value) {
    }

    public record RequestMapping(
            String sourceField, String targetField, boolean required,
            JsonNode defaultValue, String transformType) {
        @Override public JsonNode defaultValue() {
            return defaultValue == null ? null : defaultValue.deepCopy();
        }
    }

    public record Auth(String type, List<String> secretRefs, String keyName, String location) {
        public Auth { secretRefs = List.copyOf(secretRefs); }
    }

    public record ValidatedConfig(
            URI endpoint, String method, String contentType, List<Header> headers,
            List<RequestMapping> requestMapping, Auth auth, Set<Integer> successHttpStatuses,
            String businessCodePath, List<String> successBusinessCodes, String dataPath) {
        public ValidatedConfig {
            headers = List.copyOf(headers);
            requestMapping = List.copyOf(requestMapping);
            successHttpStatuses = Set.copyOf(successHttpStatuses);
            successBusinessCodes = List.copyOf(successBusinessCodes);
        }
    }
}
