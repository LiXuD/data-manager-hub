package com.dataplatform.plugin.spi;

import com.fasterxml.jackson.databind.JsonNode;

public interface ObjectCodec {

    JsonNode toTree(Object value) throws ConnectorException;

    byte[] write(Object value) throws ConnectorException;

    <T> T read(byte[] value, Class<T> type) throws ConnectorException;
}
