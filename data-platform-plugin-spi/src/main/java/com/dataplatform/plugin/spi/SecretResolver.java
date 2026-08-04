package com.dataplatform.plugin.spi;

public interface SecretResolver {

    SecretValue resolve(String secretRef) throws ConnectorException;
}
