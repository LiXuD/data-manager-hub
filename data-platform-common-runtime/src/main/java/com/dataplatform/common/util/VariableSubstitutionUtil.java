package com.dataplatform.common.util;

import java.util.Map;

/**
 * 变量替换工具类
 * 支持 ${variableName} 格式的变量替换
 */
public final class VariableSubstitutionUtil {

    private static final String VAR_PREFIX = "${";
    private static final String VAR_SUFFIX = "}";

    private VariableSubstitutionUtil() {
        // 工具类禁止实例化
    }

    /**
     * 替换字符串中的变量
     *
     * @param value   可能包含变量的字符串，如 "${token}"
     * @param context 变量上下文，key 为变量名，value 为替换值
     * @return 替换后的字符串，如果 value 为 null 则返回 null
     */
    public static String substitute(String value, Map<String, String> context) {
        if (value == null) {
            return null;
        }
        if (context == null) {
            return value;
        }
        if (value.startsWith(VAR_PREFIX) && value.endsWith(VAR_SUFFIX)) {
            String variableName = value.substring(2, value.length() - 1);
            return context.getOrDefault(variableName, value);
        }
        return value;
    }

    /**
     * 从配置 Map 中获取字符串值并替换变量
     *
     * @param config  配置 Map
     * @param key     键名
     * @param context 变量上下文
     * @return 替换后的值，如果不存在则返回 null
     */
    public static String getStringValue(Map<String, Object> config, String key, Map<String, String> context) {
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        return substitute(String.valueOf(value), context);
    }
}
