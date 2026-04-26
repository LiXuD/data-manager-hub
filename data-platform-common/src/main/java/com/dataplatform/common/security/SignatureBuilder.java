package com.dataplatform.common.security;

import cn.hutool.crypto.digest.DigestUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 签名构建器
 * 支持HMAC-SHA256和MD5签名算法
 */
public final class SignatureBuilder {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private SignatureBuilder() {
    }

    /**
     * HMAC-SHA256签名
     *
     * @param params    请求参数
     * @param secretKey 密钥
     * @return 签名字符串(十六进制)
     */
    public static String hmacSha256(Map<String, Object> params, String secretKey) {
        try {
            String content = buildSignContent(params);
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] signBytes = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(signBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA256签名失败", e);
        }
    }

    /**
     * MD5签名
     *
     * @param params    请求参数
     * @param secretKey 密钥
     * @return 签名字符串(十六进制小写)
     */
    public static String md5(Map<String, Object> params, String secretKey) {
        String content = buildSignContent(params) + secretKey;
        return DigestUtil.md5Hex(content);
    }

    /**
     * 根据签名类型生成签名
     *
     * @param params    请求参数
     * @param secretKey 密钥
     * @param signType  签名类型: HMAC_SHA256, MD5
     * @return 签名字符串
     */
    public static String sign(Map<String, Object> params, String secretKey, String signType) {
        if (signType == null || signType.isEmpty()) {
            return null;
        }

        switch (signType.toUpperCase()) {
            case "HMAC_SHA256":
            case "HMAC-SHA256":
                return hmacSha256(params, secretKey);
            case "MD5":
                return md5(params, secretKey);
            default:
                throw new IllegalArgumentException("不支持的签名类型: " + signType);
        }
    }

    /**
     * 构建待签名内容
     * 将参数按key字典序排序后拼接
     */
    private static String buildSignContent(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        // 过滤空值并排序
        return params.entrySet().stream()
            .filter(e -> e.getValue() != null && !"".equals(e.getValue()))
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
