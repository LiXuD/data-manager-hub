package com.dataplatform.plugin.spi;

import java.util.List;

public interface ConnectorPlugin extends AutoCloseable {

    PluginDescriptor descriptor();

    void initialize(PluginContext context) throws ConnectorException;

    List<ConnectorStageFactory> stageFactories();

    PluginSelfTestResult selfTest();

    @Override
    void close() throws Exception;
}
