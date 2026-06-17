package com.dataplatform.common.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 公共运行时层的 Data Masking Util。
 * <p>工具类，提供无状态的通用辅助能力。</p>
 */
public class DataMaskingUtil {

    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\d{6}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("\\d{16,19}");

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 18) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(14);
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        if (parts[0].length() <= 2) {
            return "**@" + parts[1];
        }
        return parts[0].substring(0, 2) + "***@" + parts[1];
    }

    public static String maskBankCard(String cardNo) {
        if (cardNo == null || cardNo.length() < 12) {
            return cardNo;
        }
        return "**** **** **** " + cardNo.substring(cardNo.length() - 4);
    }

    public static String maskName(String name) {
        if (name == null || name.length() < 2) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    public static String maskAddress(String address) {
        if (address == null || address.length() < 6) {
            return address;
        }
        return address.substring(0, 6) + "***";
    }

    public static Object maskValue(Object value, String fieldType) {
        if (value == null) {
            return null;
        }

        String strValue = value.toString();

        switch (fieldType) {
            case "phone":
            case "mobile":
                return maskPhone(strValue);
            case "id_card":
            case "idCard":
            case "idcard":
                return maskIdCard(strValue);
            case "email":
                return maskEmail(strValue);
            case "bank_card":
            case "bankCard":
            case "bankcard":
                return maskBankCard(strValue);
            case "name":
            case "real_name":
            case "realName":
                return maskName(strValue);
            case "address":
                return maskAddress(strValue);
            default:
                return value;
        }
    }

    public static Map<String, Object> maskMap(Map<String, Object> data, Map<String, String> fieldTypes) {
        if (data == null || fieldTypes == null) {
            return data;
        }

        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();
            String fieldType = fieldTypes.get(fieldName);

            if (fieldType != null) {
                result.put(fieldName, maskValue(value, fieldType));
            } else {
                result.put(fieldName, value);
            }
        }

        return result;
    }

    public static String maskInLog(String content) {
        if (content == null) {
            return content;
        }

        String result = content;

        result = PHONE_PATTERN.matcher(result).replaceAll("1*********");
        result = ID_CARD_PATTERN.matcher(result).replaceAll("******************");
        result = EMAIL_PATTERN.matcher(result).replaceAll("***@***.***");
        result = BANK_CARD_PATTERN.matcher(result).replaceAll("****************");

        return result;
    }
}