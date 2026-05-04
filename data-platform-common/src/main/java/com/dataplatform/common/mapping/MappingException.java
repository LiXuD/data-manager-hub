package com.dataplatform.common.mapping;

public class MappingException extends RuntimeException {

    private final String field;

    public MappingException(String message, String field) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
