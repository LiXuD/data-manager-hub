package com.dataplatform.common.plugin.artifact;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CanonicalJson {

    private CanonicalJson() {
    }

    static byte[] write(ObjectMapper mapper, JsonNode value) {
        try {
            return mapper.writeValueAsBytes(sort(value));
        } catch (JsonProcessingException exception) {
            throw new PluginArtifactException("Manifest cannot be canonicalized", exception);
        }
    }

    private static JsonNode sort(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = ((ObjectNode) value).objectNode();
            List<String> fields = new ArrayList<>();
            value.fieldNames().forEachRemaining(fields::add);
            fields.sort(Comparator.naturalOrder());
            fields.forEach(field -> result.set(field, sort(value.get(field))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = ((ArrayNode) value).arrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        return value;
    }
}
