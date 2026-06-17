package com.dataplatform.common.mapping;

/**
 * 公共运行时层参数映射的 Mapping Exception。
 * <p>业务异常类型，用于表达可预期的领域错误。</p>
 */
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
