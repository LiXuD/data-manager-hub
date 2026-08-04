package com.dataplatform.access.connector.runtime;

import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.dataplatform.plugin.spi.SecretResolver;
import com.dataplatform.plugin.spi.SecretValue;
import com.dataplatform.common.plugin.runtime.ConnectorStageSecretScope;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;

/** Supplies only the current vendor call's resolved secrets to plugin stages. */
@Component
public class ScopedConnectorSecretResolver implements SecretResolver, ConnectorStageSecretScope {

    private final ThreadLocal<ExecutionSecrets> current = new ThreadLocal<>();

    @Override
    public SecretValue resolve(String secretRef) throws ConnectorException {
        ExecutionSecrets execution = current.get();
        String value = execution != null && execution.allowedRefs != null
                && execution.allowedRefs.contains(secretRef) ? execution.secrets.get(secretRef) : null;
        if (value == null) {
            throw new ConnectorException(ErrorCategory.AUTH_SECURITY_ERROR, "SECRET_REF_UNAVAILABLE",
                    "Required connector secret is unavailable", RequestDeliveryState.NOT_SENT);
        }
        return new SecretValue(value.toCharArray());
    }

    public <T> T withSecrets(Map<String, String> secrets, Callable<T> action) {
        if (current.get() != null) {
            throw new IllegalStateException("Nested connector secret scope is not allowed");
        }
        current.set(new ExecutionSecrets(secrets == null ? Map.of() : Map.copyOf(secrets)));
        try {
            return action.call();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Connector secret scope failed", ex);
        } finally {
            current.remove();
        }
    }

    public boolean contains(String secretRef) {
        return current.get() != null && current.get().secrets.containsKey(secretRef);
    }

    @Override
    public void enter(JsonNode config) {
        ExecutionSecrets execution = current.get();
        if (execution == null || execution.allowedRefs != null) {
            throw new IllegalStateException("Connector stage secret scope is unavailable or already active");
        }
        Set<String> refs = new LinkedHashSet<>();
        collectRefs(config, null, refs);
        execution.allowedRefs = Set.copyOf(refs);
    }

    @Override
    public void leave() {
        ExecutionSecrets execution = current.get();
        if (execution != null) execution.allowedRefs = null;
    }

    private void collectRefs(JsonNode node, String fieldName, Set<String> refs) {
        if (node == null || node.isNull() || node.isMissingNode()) return;
        String normalized = fieldName == null ? "" : fieldName.toLowerCase(java.util.Locale.ROOT)
                .replace("_", "").replace("-", "");
        if ("secretrefs".equals(normalized)) {
            if (node.isTextual() && !node.asText().isBlank()) refs.add(node.asText());
            else if (node.isObject()) node.elements().forEachRemaining(child -> collectRefs(child, fieldName, refs));
            else if (node.isArray()) node.forEach(child -> collectRefs(child, fieldName, refs));
            return;
        }
        if (node.isTextual() && "secretref".equals(normalized)) {
            if (!node.asText().isBlank()) refs.add(node.asText());
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectRefs(entry.getValue(), entry.getKey(), refs));
        } else if (node.isArray()) {
            node.forEach(child -> collectRefs(child, fieldName, refs));
        }
    }

    private static final class ExecutionSecrets {
        private final Map<String, String> secrets;
        private Set<String> allowedRefs;

        private ExecutionSecrets(Map<String, String> secrets) {
            this.secrets = secrets;
        }
    }
}
