package com.dataplatform.plugin.spi;

import java.util.Map;

public interface PluginLogger {

    void debug(String event, Map<String, ?> safeFields);

    void info(String event, Map<String, ?> safeFields);

    void warn(String event, Map<String, ?> safeFields);

    void error(String event, Map<String, ?> safeFields);
}
