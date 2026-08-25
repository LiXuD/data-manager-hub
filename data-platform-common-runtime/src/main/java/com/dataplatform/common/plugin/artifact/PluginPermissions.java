package com.dataplatform.common.plugin.artifact;

import java.util.List;

public record PluginPermissions(List<String> networkProtocols, List<String> networkHosts) {

    public PluginPermissions {
        networkProtocols = networkProtocols == null ? List.of() : networkProtocols.stream()
                .map(String::toLowerCase).distinct().toList();
        networkHosts = networkHosts == null ? List.of() : networkHosts.stream()
                .map(String::toLowerCase).distinct().toList();
    }
}
