package com.dataplatform.common.enums;
/**
 * 代码枚举接口
 * 所有使用 code 字段的枚举都应实现此接口
 */
public interface CodeEnum {
    /**
     * 获取枚举代码
     */
    String getCode();
    /**
     * 获取枚举描述
     */
    String getDescription();
}
