package com.dataplatform.plugin.spi;

public interface ConnectorStage {

    StageCapability capability();

    void execute(ConnectorExchange exchange, StageExecutionContext context) throws ConnectorException;
}
