package com.dataplatform.common.util;

/**
 * 日志截断工具类。
 * <p>统一处理大报文截断，避免日志中输出过长内容。</p>
 */
public final class LogTruncationUtil {

    public static final int SHORT = 2048;
    public static final int MEDIUM = 4096;
    public static final int FULL = 8192;

    private LogTruncationUtil() {}

    public static String truncate(Object obj, int maxLength) {
        if (obj == null) {
            return "null";
        }
        String str = obj instanceof String ? (String) obj : obj.toString();
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...[truncated " + str.length() + " chars]";
    }
}
