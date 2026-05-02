package com.dataplatform.common.enums;

/**
 * 枚举工具类
 */
public final class EnumUtils {

    private EnumUtils() {
    }

    /**
     * 根据code获取枚举值
     *
     * @param enumClass 枚举类
     * @param code      代码值
     * @param <T>       枚举类型
     * @return 枚举值，未找到返回null
     */
    public static <T extends Enum<T> & CodeEnum> T fromCode(Class<T> enumClass, String code) {
        if (code == null) {
            return null;
        }
        for (T e : enumClass.getEnumConstants()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 根据code获取枚举值，支持默认值
     *
     * @param enumClass   枚举类
     * @param code        代码值
     * @param defaultValue 默认值
     * @param <T>         枚举类型
     * @return 枚举值，未找到返回默认值
     */
    public static <T extends Enum<T> & CodeEnum> T fromCode(Class<T> enumClass, String code, T defaultValue) {
        T result = fromCode(enumClass, code);
        return result != null ? result : defaultValue;
    }
}
