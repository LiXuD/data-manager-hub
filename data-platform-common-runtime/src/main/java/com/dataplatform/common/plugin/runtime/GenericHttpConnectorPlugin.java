package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.AbstractVendorConnectorPlugin;
import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.IdempotencyPolicy;
import com.dataplatform.plugin.spi.PluginDescriptor;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.SecretValue;
import com.dataplatform.plugin.spi.VendorConnectorInvocation;
import com.dataplatform.plugin.spi.VendorParseResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Host-owned one-request HTTP implementation for standard connector products. */
public final class GenericHttpConnectorPlugin extends AbstractVendorConnectorPlugin {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_INPUT_BYTES = 1024 * 1024;
    private static final int MAX_XML_DEPTH = 32;
    private static final int MAX_XML_NODES = 10_000;
    private static final int MAX_XML_TEXT = 1024 * 1024;

    @Override
    public PluginDescriptor descriptor() {
        return GenericHttpConnectorMetadata.descriptor();
    }

    @Override
    protected ConnectorRequest buildRequest(VendorConnectorInvocation invocation) throws ConnectorException {
        try {
            GenericHttpConnectorConfigValidator.ValidatedConfig config = validate(invocation.pluginConfig());
            invocation.cancellationToken().throwIfCancelled();
            Duration remaining = invocation.deadline().remaining();
            if (remaining == null || remaining.isZero() || remaining.isNegative()
                    || invocation.deadline().isExpired()) {
                throw failure(ErrorCategory.TRANSPORT_TIMEOUT, "GENERIC_HTTP_DEADLINE_EXCEEDED",
                        "Generic HTTP execution deadline was exceeded", RequestDeliveryState.NOT_SENT, null);
            }
            ObjectNode parameters = mappedParameters(invocation.standardInput(), config.requestMapping());
            boolean queryMethod = "GET".equals(config.method()) || "HEAD".equals(config.method());
            Map<String, List<String>> query = queryMethod ? flatten(parameters) : Map.of();
            byte[] body = queryMethod ? new byte[0] : requestBody(parameters, config.contentType());
            Map<String, List<String>> headers = new LinkedHashMap<>();
            for (var header : config.headers()) headers.put(header.name(), List.of(header.value()));
            IdempotencyPolicy idempotency = queryMethod
                    ? IdempotencyPolicy.IDEMPOTENT : IdempotencyPolicy.NON_IDEMPOTENT;
            return new ConnectorRequest(config.method(), config.endpoint(), headers, query,
                    config.contentType(), body, remaining, remaining, remaining,
                    idempotency, null, GenericHttpConnectorConfigValidator.MAX_RESPONSE_BYTES);
        } catch (ConnectorException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(ErrorCategory.CONFIGURATION_ERROR, "GENERIC_HTTP_CONFIG_INVALID",
                    "Generic HTTP configuration is invalid", RequestDeliveryState.NOT_SENT, exception);
        }
    }

    @Override
    protected ConnectorRequest processRequest(
            VendorConnectorInvocation invocation, ConnectorRequest request) throws ConnectorException {
        try {
            GenericHttpConnectorConfigValidator.ValidatedConfig config = validate(invocation.pluginConfig());
            invocation.cancellationToken().throwIfCancelled();
            Map<String, List<String>> headers = mutable(request.headers());
            Map<String, List<String>> query = mutable(request.query());
            switch (config.auth().type()) {
                case "NONE" -> { }
                case "BEARER" -> putHeader(headers, "Authorization",
                        "Bearer " + resolve(invocation, config.auth().secretRefs().getFirst()));
                case "BASIC" -> {
                    String username = resolve(invocation, config.auth().secretRefs().get(0));
                    String password = resolve(invocation, config.auth().secretRefs().get(1));
                    String encoded = Base64.getEncoder().encodeToString(
                            (username + ":" + password).getBytes(StandardCharsets.UTF_8));
                    putHeader(headers, "Authorization", "Basic " + encoded);
                }
                case "API_KEY" -> {
                    String value = resolve(invocation, config.auth().secretRefs().getFirst());
                    if ("header".equals(config.auth().location())) {
                        putHeader(headers, config.auth().keyName(), value);
                    } else {
                        if (query.containsKey(config.auth().keyName())) throw invalidAuth();
                        query.put(config.auth().keyName(), List.of(value));
                    }
                }
                default -> throw invalidAuth();
            }
            return new ConnectorRequest(request.method(), request.url(), headers, query,
                    request.contentType(), request.body(), request.connectTimeout(), request.readTimeout(),
                    request.totalTimeout(), request.idempotencyPolicy(), request.idempotencyKey(),
                    request.maxResponseBytes());
        } catch (ConnectorException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(ErrorCategory.AUTH_SECURITY_ERROR, "GENERIC_HTTP_AUTH_ERROR",
                    "Generic HTTP authentication failed", RequestDeliveryState.NOT_SENT, exception);
        }
    }

    @Override
    protected VendorParseResult parseResponse(
            VendorConnectorInvocation invocation, ConnectorRawResponse response) throws ConnectorException {
        try {
            GenericHttpConnectorConfigValidator.ValidatedConfig config = validate(invocation.pluginConfig());
            if (!config.successHttpStatuses().contains(response.statusCode())) {
                throw failure(ErrorCategory.TRANSPORT_HTTP_ERROR, "GENERIC_HTTP_STATUS_ERROR",
                        "Vendor returned an unsuccessful HTTP status", RequestDeliveryState.SENT, null);
            }
            byte[] body = response.body();
            if (body.length == 0) throw parseFailure("GENERIC_HTTP_EMPTY_RESPONSE");
            if (body.length > GenericHttpConnectorConfigValidator.MAX_RESPONSE_BYTES) {
                throw parseFailure("GENERIC_HTTP_RESPONSE_TOO_LARGE");
            }
            JsonNode document = parseDocument(body, contentType(response.headers()));
            if (!document.isObject()) throw parseFailure("GENERIC_HTTP_RESPONSE_NOT_OBJECT");
            JsonNode selected = config.dataPath() == null ? document : path(document, config.dataPath());
            if (selected == null || !selected.isObject()) throw parseFailure("GENERIC_HTTP_DATA_PATH_INVALID");
            Map<String, Object> data = immutableMap((ObjectNode) selected);
            if (config.businessCodePath() != null) {
                JsonNode codeNode = path(document, config.businessCodePath());
                if (codeNode == null || !codeNode.isValueNode() || codeNode.isNull()) {
                    throw parseFailure("GENERIC_HTTP_BUSINESS_CODE_MISSING");
                }
                String code = codeNode.asText();
                if (!config.successBusinessCodes().contains(code)) {
                    return VendorParseResult.rejected(data, code, "Vendor rejected the request");
                }
                return VendorParseResult.success(data, code,
                        com.dataplatform.plugin.spi.BillingSignal.ELIGIBLE,
                        com.dataplatform.plugin.spi.CacheSignal.CACHEABLE, null);
            }
            return VendorParseResult.success(data);
        } catch (ConnectorException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(ErrorCategory.RESPONSE_PARSE_ERROR, "GENERIC_HTTP_PARSE_ERROR",
                    "Generic HTTP response could not be parsed", RequestDeliveryState.SENT, exception);
        }
    }

    private GenericHttpConnectorConfigValidator.ValidatedConfig validate(JsonNode config) {
        return GenericHttpConnectorConfigValidator.validate(config, ignored -> true);
    }

    private ObjectNode mappedParameters(
            JsonNode standardInput,
            List<GenericHttpConnectorConfigValidator.RequestMapping> mappings) throws ConnectorException {
        if (standardInput == null || !standardInput.isObject()) {
            throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "GENERIC_HTTP_INPUT_INVALID",
                    "Generic HTTP input must be an object", RequestDeliveryState.NOT_SENT, null);
        }
        try {
            if (JSON.writeValueAsBytes(standardInput).length > MAX_INPUT_BYTES) {
                throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "GENERIC_HTTP_INPUT_TOO_LARGE",
                        "Generic HTTP input is too large", RequestDeliveryState.NOT_SENT, null);
            }
        } catch (ConnectorException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "GENERIC_HTTP_INPUT_INVALID",
                    "Generic HTTP input is invalid", RequestDeliveryState.NOT_SENT, exception);
        }
        ObjectNode source = standardInput.deepCopy();
        ObjectNode result = source.deepCopy();
        List<ResolvedMapping> resolved = new ArrayList<>();
        for (var mapping : mappings) {
            JsonNode value = path(source, mapping.sourceField());
            boolean missing = value == null || value.isNull();
            if (missing && mapping.defaultValue() != null && !mapping.defaultValue().isNull()) {
                value = mapping.defaultValue();
                missing = false;
            }
            if (missing) {
                if (mapping.required()) {
                    throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "GENERIC_HTTP_REQUIRED_INPUT_MISSING",
                            "Generic HTTP required input is missing", RequestDeliveryState.NOT_SENT, null);
                }
                continue;
            }
            value = transform(value, mapping.transformType());
            resolved.add(new ResolvedMapping(mapping.sourceField(), mapping.targetField(), value));
        }
        List<String> removed = new ArrayList<>();
        for (ResolvedMapping mapping : resolved) {
            if (!mapping.source().equals(mapping.target()) && !removed.contains(mapping.source())) {
                remove(result, mapping.source());
                removed.add(mapping.source());
            }
        }
        for (ResolvedMapping mapping : resolved) set(result, mapping.target(), mapping.value());
        return (ObjectNode) sort(result);
    }

    private byte[] requestBody(ObjectNode parameters, String contentType) throws ConnectorException {
        try {
            if (GenericHttpConnectorConfigValidator.FORM_CONTENT_TYPE.equals(contentType)) {
                List<String> pairs = new ArrayList<>();
                flatten(parameters).forEach((key, values) -> values.forEach(value -> pairs.add(
                        encode(key) + "=" + encode(value))));
                return String.join("&", pairs).getBytes(StandardCharsets.UTF_8);
            }
            return JSON.writeValueAsBytes(sort(parameters));
        } catch (Exception exception) {
            throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "GENERIC_HTTP_BODY_INVALID",
                    "Generic HTTP request body could not be built", RequestDeliveryState.NOT_SENT, exception);
        }
    }

    private Map<String, List<String>> flatten(ObjectNode parameters) throws ConnectorException {
        TreeMap<String, List<String>> result = new TreeMap<>();
        var fields = parameters.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            result.put(entry.getKey(), values(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private List<String> values(JsonNode value) throws ConnectorException {
        if (value == null || value.isNull()) return List.of("");
        if (value.isValueNode()) return List.of(value.asText());
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : value) values.add(item.isContainerNode() ? canonical(item) : item.asText());
            return List.copyOf(values);
        }
        if (value.isObject()) return List.of(canonical(value));
        throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "GENERIC_HTTP_PARAMETER_INVALID",
                "Generic HTTP parameter is invalid", RequestDeliveryState.NOT_SENT, null);
    }

    private String canonical(JsonNode value) throws ConnectorException {
        try { return JSON.writeValueAsString(sort(value)); }
        catch (Exception exception) {
            throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "GENERIC_HTTP_PARAMETER_INVALID",
                    "Generic HTTP parameter is invalid", RequestDeliveryState.NOT_SENT, exception);
        }
    }

    private String resolve(VendorConnectorInvocation invocation, String ref) throws ConnectorException {
        try (SecretValue value = invocation.secretResolver().resolve(ref)) {
            return value.materialize();
        } catch (ConnectorException exception) {
            throw invalidAuth();
        } catch (RuntimeException exception) {
            throw invalidAuth();
        }
    }

    private void putHeader(Map<String, List<String>> headers, String name, String value) throws ConnectorException {
        if (headers.keySet().stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) {
            throw invalidAuth();
        }
        headers.put(name, List.of(value));
    }

    private JsonNode parseDocument(byte[] body, String contentType) throws ConnectorException {
        try {
            String mediaType = contentType.split(";", 2)[0].trim();
            if ("application/json".equals(mediaType) || mediaType.endsWith("+json")) {
                return JSON.readTree(body);
            }
            if ("application/xml".equals(mediaType) || "text/xml".equals(mediaType)
                    || mediaType.endsWith("+xml")) {
                return xml(body);
            }
            throw parseFailure("GENERIC_HTTP_CONTENT_TYPE_UNSUPPORTED");
        } catch (ConnectorException exception) {
            throw exception;
        } catch (Exception exception) {
            throw parseFailure("GENERIC_HTTP_PARSE_ERROR");
        }
    }

    private JsonNode xml(byte[] body) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(body)).getDocumentElement();
        int[] count = {0};
        JsonNode value = xmlElement(root, 0, count);
        return value.isObject() ? value : JSON.createObjectNode().set(root.getTagName(), value);
    }

    private JsonNode xmlElement(Element element, int depth, int[] count) throws ConnectorException {
        if (depth > MAX_XML_DEPTH || ++count[0] > MAX_XML_NODES) {
            throw parseFailure("GENERIC_HTTP_XML_LIMIT_EXCEEDED");
        }
        Map<String, List<JsonNode>> children = new TreeMap<>();
        StringBuilder text = new StringBuilder();
        NodeList nodes = element.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node child = nodes.item(index);
            if (child instanceof Element nested) {
                children.computeIfAbsent(nested.getTagName(), ignored -> new ArrayList<>())
                        .add(xmlElement(nested, depth + 1, count));
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(child.getNodeValue());
                if (text.length() > MAX_XML_TEXT) throw parseFailure("GENERIC_HTTP_XML_LIMIT_EXCEEDED");
            }
        }
        if (children.isEmpty()) return JSON.getNodeFactory().textNode(text.toString().trim());
        if (!text.toString().isBlank()) throw parseFailure("GENERIC_HTTP_XML_MIXED_CONTENT");
        ObjectNode result = JSON.createObjectNode();
        children.forEach((name, values) -> {
            if (values.size() == 1) result.set(name, values.getFirst());
            else {
                ArrayNode array = result.putArray(name);
                values.forEach(array::add);
            }
        });
        return result;
    }

    private String contentType(Map<String, List<String>> headers) {
        return headers.entrySet().stream()
                .filter(entry -> "content-type".equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream()).findFirst()
                .orElse("").toLowerCase(Locale.ROOT);
    }

    private JsonNode path(JsonNode root, String path) {
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (current == null) return null;
            if (current.isObject()) current = current.get(segment);
            else if (current.isArray() && segment.matches("0|[1-9][0-9]{0,5}")) {
                int index = Integer.parseInt(segment);
                current = index < current.size() ? current.get(index) : null;
            } else return null;
        }
        return current == null || current.isMissingNode() ? null : current;
    }

    private void remove(ObjectNode root, String path) {
        String[] segments = path.split("\\.");
        JsonNode current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            current = current == null ? null : current.get(segments[index]);
            if (current == null || !current.isObject()) return;
        }
        if (current instanceof ObjectNode object) object.remove(segments[segments.length - 1]);
    }

    private void set(ObjectNode root, String path, JsonNode value) throws ConnectorException {
        String[] segments = path.split("\\.");
        ObjectNode current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            JsonNode existing = current.get(segments[index]);
            if (existing == null || existing.isNull()) current = current.putObject(segments[index]);
            else if (existing instanceof ObjectNode object) current = object;
            else throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "GENERIC_HTTP_MAPPING_COLLISION",
                        "Generic HTTP request mapping collides with input", RequestDeliveryState.NOT_SENT, null);
        }
        current.set(segments[segments.length - 1], value.deepCopy());
    }

    private JsonNode transform(JsonNode value, String transform) throws ConnectorException {
        if ("none".equals(transform)) return value.deepCopy();
        if (!value.isTextual()) {
            throw failure(ErrorCategory.REQUEST_BUILD_ERROR, "GENERIC_HTTP_TRANSFORM_INVALID",
                    "Generic HTTP transform requires text", RequestDeliveryState.NOT_SENT, null);
        }
        String text = value.asText();
        return JSON.getNodeFactory().textNode(switch (transform) {
            case "trim" -> text.trim();
            case "uppercase" -> text.toUpperCase(Locale.ROOT);
            case "lowercase" -> text.toLowerCase(Locale.ROOT);
            default -> throw new IllegalStateException("unknown transform");
        });
    }

    private JsonNode sort(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            List<String> fields = new ArrayList<>();
            value.fieldNames().forEachRemaining(fields::add);
            fields.stream().sorted().forEach(field -> result.set(field, sort(value.get(field))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = JSON.createArrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        return value.deepCopy();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> immutableMap(ObjectNode value) {
        Map<String, Object> result = JSON.convertValue(sort(value), LinkedHashMap.class);
        return VendorParseResult.success(result).data();
    }

    private Map<String, List<String>> mutable(Map<String, List<String>> source) {
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, new ArrayList<>(value)));
        return result;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private ConnectorException invalidAuth() {
        return failure(ErrorCategory.AUTH_SECURITY_ERROR, "GENERIC_HTTP_AUTH_ERROR",
                "Generic HTTP authentication failed", RequestDeliveryState.NOT_SENT, null);
    }

    private ConnectorException parseFailure(String code) {
        return failure(ErrorCategory.RESPONSE_PARSE_ERROR, code,
                "Generic HTTP response could not be parsed", RequestDeliveryState.SENT, null);
    }

    private ConnectorException failure(
            ErrorCategory category, String code, String message,
            RequestDeliveryState delivery, Throwable cause) {
        return new ConnectorException(category, code, message, delivery, cause);
    }

    private record ResolvedMapping(String source, String target, JsonNode value) {
    }

}
