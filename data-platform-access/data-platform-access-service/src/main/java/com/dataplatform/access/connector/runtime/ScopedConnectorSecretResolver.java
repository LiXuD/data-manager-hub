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
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Supplies only the current vendor call's resolved secrets to plugin stages. */
@Component
public class ScopedConnectorSecretResolver implements SecretResolver, ConnectorStageSecretScope {

    private final ThreadLocal<ExecutionSecrets> current = new ThreadLocal<>();

    @Override
    public SecretValue resolve(String secretRef) throws ConnectorException {
        ExecutionSecrets execution = current.get();
        String value = execution != null && execution.allowedRefs != null
                && execution.allowedRefs.contains(secretRef) ? execution.stageSecrets.get(secretRef) : null;
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

    /** Resolves only the references declared by the stage that is about to execute. */
    public <T> T withSecretProvider(Function<Set<String>, Map<String, String>> provider,
                                    Callable<T> action) {
        if (current.get() != null) {
            throw new IllegalStateException("Nested connector secret scope is not allowed");
        }
        current.set(new ExecutionSecrets(java.util.Objects.requireNonNull(provider, "provider")));
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
        ExecutionSecrets execution = current.get();
        return execution != null && (execution.secrets.containsKey(secretRef)
                || execution.stageSecrets.containsKey(secretRef));
    }

    @Override
    public Iterable<String> sensitiveValues() {
        ExecutionSecrets execution = current.get();
        return execution == null ? java.util.List.of() : java.util.List.copyOf(execution.sensitiveValues);
    }

    @Override
    public void enter(JsonNode config) {
        enter(config, null);
    }

    @Override
    public void enter(JsonNode config, Set<String> declaredReferences) {
        ExecutionSecrets execution = current.get();
        if (execution == null || execution.allowedRefs != null) {
            throw new IllegalStateException("Connector stage secret scope is unavailable or already active");
        }
        Set<String> refs = new LinkedHashSet<>();
        if (declaredReferences == null) collectRefs(config, null, refs);
        else refs.addAll(declaredReferences);
        Map<String, String> resolved;
        if (execution.provider == null) {
            if (!execution.secrets.keySet().containsAll(refs)) {
                throw new IllegalStateException("Connector stage secret resolution was incomplete");
            }
            Map<String, String> selected = new java.util.LinkedHashMap<>();
            refs.forEach(ref -> selected.put(ref, execution.secrets.get(ref)));
            resolved = Map.copyOf(selected);
        } else {
            resolved = execution.provider.apply(Set.copyOf(refs));
        }
        resolved = resolved == null ? Map.of() : Map.copyOf(resolved);
        if (execution.provider != null && !resolved.keySet().equals(refs)) {
            throw new IllegalStateException("Connector stage secret resolution was incomplete or over-broad");
        }
        execution.stageSecrets = resolved;
        execution.sensitiveValues.addAll(resolved.values());
        execution.allowedRefs = Set.copyOf(refs);
    }

    @Override
    public void leave() {
        ExecutionSecrets execution = current.get();
        if (execution != null) {
            execution.allowedRefs = null;
            execution.stageSecrets = Map.of();
        }
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
        private final Function<Set<String>, Map<String, String>> provider;
        private final Set<String> sensitiveValues = new LinkedHashSet<>();
        private Map<String, String> stageSecrets = Map.of();
        private Set<String> allowedRefs;

        private ExecutionSecrets(Map<String, String> secrets) {
            this.secrets = secrets;
            this.provider = null;
            this.sensitiveValues.addAll(secrets.values());
        }

        private ExecutionSecrets(Function<Set<String>, Map<String, String>> provider) {
            this.secrets = Map.of();
            this.provider = provider;
        }
    }
}
