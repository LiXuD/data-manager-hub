package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.PluginLogger;
import org.slf4j.Logger;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Slf4jPluginLogger implements PluginLogger {

    private static final int MAX_VALUE_LENGTH = 512;
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "pluginid", "pluginversion", "stagekey", "capability", "errorcategory",
            "errorcode", "status", "durationms", "attempt", "instanceid");
    private static final Set<String> SENSITIVE_MARKERS = Set.of(
            "secret", "password", "token", "authorization", "privatekey", "credential", "body", "response");
    private final Logger logger;

    public Slf4jPluginLogger(Logger logger) {
        this.logger = logger;
    }

    @Override public void debug(String event, Map<String, ?> fields) { logger.debug("{} {}", event(event), sanitize(fields)); }
    @Override public void info(String event, Map<String, ?> fields) { logger.info("{} {}", event(event), sanitize(fields)); }
    @Override public void warn(String event, Map<String, ?> fields) { logger.warn("{} {}", event(event), sanitize(fields)); }
    @Override public void error(String event, Map<String, ?> fields) { logger.error("{} {}", event(event), sanitize(fields)); }

    private Map<String, Object> sanitize(Map<String, ?> fields) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        fields.forEach((key, value) -> {
            String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
            if (SENSITIVE_MARKERS.stream().anyMatch(normalized::contains)) {
                safe.put(key, "[REDACTED]");
            } else if (ALLOWED_FIELDS.contains(normalized)) {
                String text = String.valueOf(value);
                safe.put(key, text.length() <= MAX_VALUE_LENGTH ? text : text.substring(0, MAX_VALUE_LENGTH));
            }
        });
        return Map.copyOf(safe);
    }

    private String event(String event) {
        if (event == null || !event.matches("[A-Za-z0-9_.:-]{1,128}")) {
            return "plugin_event";
        }
        return event;
    }
}
