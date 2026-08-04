package com.dataplatform.plugin.spi;

public record PluginSelfTestResult(boolean successful, String safeMessage) {

    public static PluginSelfTestResult success() {
        return new PluginSelfTestResult(true, "OK");
    }

    public static PluginSelfTestResult failure(String safeMessage) {
        return new PluginSelfTestResult(false, safeMessage == null ? "self-test failed" : safeMessage);
    }
}
