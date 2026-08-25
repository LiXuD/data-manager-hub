package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ErrorCategory;
import com.dataplatform.plugin.spi.ObjectCodec;
import com.dataplatform.plugin.spi.RequestDeliveryState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JacksonObjectCodec implements ObjectCodec {

    private final ObjectMapper mapper;

    public JacksonObjectCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public JsonNode toTree(Object value) throws ConnectorException {
        try {
            return mapper.valueToTree(value);
        } catch (IllegalArgumentException exception) {
            throw failure(exception);
        }
    }

    @Override
    public byte[] write(Object value) throws ConnectorException {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception exception) {
            throw failure(exception);
        }
    }

    @Override
    public <T> T read(byte[] value, Class<T> type) throws ConnectorException {
        try {
            return mapper.readValue(value, type);
        } catch (Exception exception) {
            throw failure(exception);
        }
    }

    private ConnectorException failure(Exception cause) {
        return new ConnectorException(ErrorCategory.PLUGIN_INTERNAL_ERROR, "OBJECT_CODEC_ERROR",
                "Plugin object conversion failed", RequestDeliveryState.NOT_SENT, cause);
    }
}
