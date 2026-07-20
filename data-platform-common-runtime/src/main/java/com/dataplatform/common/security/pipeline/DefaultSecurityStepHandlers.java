package com.dataplatform.common.security.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

final class DefaultSecurityStepHandlers {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private DefaultSecurityStepHandlers() {
    }

    static List<SecurityStepHandler> create() {
        return List.of(
                new FieldSelectHandler(),
                new GenerateHandler(),
                new CanonicalizeHandler(),
                new DigestHandler(),
                new HmacHandler(),
                new SignHandler(),
                new EncryptHandler(false),
                new EncryptHandler(true),
                new VerifyHandler(),
                new CodecHandler(false),
                new CodecHandler(true),
                new InjectHandler(),
                new RemoveFieldHandler()
        );
    }

    private abstract static class BaseHandler implements SecurityStepHandler {
        protected Map<String, Object> config(SecurityStepConfig step) {
            return step.getConfig() == null ? Map.of() : step.getConfig();
        }

        protected String value(SecurityStepConfig step, String key, String defaultValue) {
            return SecurityPipelineExecutor.string(config(step), key, defaultValue);
        }

        protected boolean bool(SecurityStepConfig step, String key, boolean defaultValue) {
            Object raw = config(step).get(key);
            return raw == null ? defaultValue : Boolean.parseBoolean(String.valueOf(raw));
        }

        protected int integer(SecurityStepConfig step, String key, int defaultValue) {
            Object raw = config(step).get(key);
            return raw == null ? defaultValue : Integer.parseInt(String.valueOf(raw));
        }

        protected void required(SecurityStepConfig step, String key) {
            if (value(step, key, null) == null || value(step, key, null).isBlank()) {
                throw new IllegalArgumentException(step.getStepType() + " 缺少配置: " + key);
            }
        }

        protected String oneOf(SecurityStepConfig step, String key, String defaultValue, String... supported) {
            String actual = value(step, key, defaultValue).toUpperCase(Locale.ROOT);
            for (String candidate : supported) {
                if (candidate.equals(actual)) {
                    return actual;
                }
            }
            throw new IllegalArgumentException(step.getStepType() + " 不支持的" + key + ": " + actual);
        }

        protected Object input(SecurityExecutionContext context, SecurityStepConfig step) {
            return context.resolveInput(value(step, "inputFrom", null));
        }
    }

    private static final class FieldSelectHandler extends BaseHandler {
        @Override public SecurityStepType type() { return SecurityStepType.FIELD_SELECT; }
        @Override public void validate(SecurityStepConfig step) { required(step, "fields"); }

        @Override
        public Object execute(SecurityExecutionContext context, SecurityStepConfig step) {
            Map<String, Object> selected = new LinkedHashMap<>();
            Object source = input(context, step);
            if (!(source instanceof Map<?, ?> sourceMap)) {
                throw new IllegalArgumentException("FIELD_SELECT 输入必须是对象");
            }
            for (String field : stringList(config(step).get("fields"))) {
                if (sourceMap.containsKey(field)) {
                    selected.put(field, sourceMap.get(field));
                }
            }
            if (bool(step, "replaceParams", false)) {
                context.getParams().clear();
                context.getParams().putAll(selected);
            }
            return selected;
        }
    }

    private static final class GenerateHandler extends BaseHandler {
        @Override public SecurityStepType type() { return SecurityStepType.GENERATE; }
        @Override public void validate(SecurityStepConfig step) {
            required(step, "fieldName");
            String generator = oneOf(step, "generator", "TIMESTAMP_MILLIS",
                    "TIMESTAMP_SECONDS", "TIMESTAMP_MILLIS", "UUID", "NONCE", "CONSTANT");
            oneOf(step, "location", "PARAM", "PARAM", "HEADER", "QUERY");
            if ("NONCE".equals(generator) && (integer(step, "length", 16) < 1 || integer(step, "length", 16) > 4096)) {
                throw new IllegalArgumentException("NONCE长度必须在1到4096之间");
            }
            if ("CONSTANT".equals(generator) && !config(step).containsKey("value")) {
                throw new IllegalArgumentException("GENERATE 使用CONSTANT时缺少配置: value");
            }
        }

        @Override
        public Object execute(SecurityExecutionContext context, SecurityStepConfig step) {
            String generator = value(step, "generator", "TIMESTAMP_MILLIS").toUpperCase(Locale.ROOT);
            Object generated = switch (generator) {
                case "TIMESTAMP_SECONDS" -> System.currentTimeMillis() / 1000;
                case "TIMESTAMP_MILLIS" -> System.currentTimeMillis();
                case "UUID" -> UUID.randomUUID().toString();
                case "NONCE" -> randomHex(integer(step, "length", 16));
                case "CONSTANT" -> config(step).get("value");
                default -> throw new IllegalArgumentException("不支持的生成器: " + generator);
            };
            inject(context, value(step, "location", "PARAM"), value(step, "fieldName", null), generated);
            return generated;
        }
    }

    private static final class CanonicalizeHandler extends BaseHandler {
        @Override public SecurityStepType type() { return SecurityStepType.CANONICALIZE; }
        @Override public void validate(SecurityStepConfig step) {
            String fieldOrder = oneOf(step, "fieldOrder", "KEY_ASC", "KEY_ASC", "KEY_DESC", "EXPLICIT", "NONE");
            oneOf(step, "nullPolicy", "IGNORE", "IGNORE", "KEEP");
            if ("EXPLICIT".equals(fieldOrder) && stringList(config(step).get("fields")).isEmpty()) {
                throw new IllegalArgumentException("CANONICALIZE 使用EXPLICIT排序时必须配置fields");
            }
        }

        @Override
        public Object execute(SecurityExecutionContext context, SecurityStepConfig step) {
            Object source = input(context, step);
            if (!(source instanceof Map<?, ?> sourceMap)) {
                return value(step, "prefix", "") + stringify(source) + value(step, "suffix", "");
            }
            List<Map.Entry<String, Object>> entries = new ArrayList<>();
            List<String> explicitFields = stringList(config(step).get("fields"));
            if (!explicitFields.isEmpty()) {
                for (String field : explicitFields) {
                    if (sourceMap.containsKey(field)) {
                        entries.add(new java.util.AbstractMap.SimpleImmutableEntry<>(field, sourceMap.get(field)));
                    }
                }
            } else {
                sourceMap.forEach((key, fieldValue) -> entries.add(
                        new java.util.AbstractMap.SimpleImmutableEntry<>(String.valueOf(key), fieldValue)));
            }
            String nullPolicy = value(step, "nullPolicy", "IGNORE").toUpperCase(Locale.ROOT);
            entries.removeIf(entry -> "IGNORE".equals(nullPolicy)
                    && (entry.getValue() == null || String.valueOf(entry.getValue()).isEmpty()));
            String order = value(step, "fieldOrder", "KEY_ASC").toUpperCase(Locale.ROOT);
            if ("KEY_ASC".equals(order)) {
                entries.sort(Map.Entry.comparingByKey());
            } else if ("KEY_DESC".equals(order)) {
                entries.sort(Map.Entry.comparingByKey(Comparator.reverseOrder()));
            } else if (!"EXPLICIT".equals(order) && !"NONE".equals(order)) {
                throw new IllegalArgumentException("不支持的字段排序: " + order);
            }
            String pairSeparator = value(step, "pairSeparator", "&");
            String keyValueSeparator = value(step, "keyValueSeparator", "=");
            boolean includeKey = bool(step, "includeKey", true);
            StringBuilder content = new StringBuilder(value(step, "prefix", ""));
            for (Map.Entry<String, Object> entry : entries) {
                if (content.length() > value(step, "prefix", "").length()) {
                    content.append(pairSeparator);
                }
                if (includeKey) {
                    content.append(entry.getKey()).append(keyValueSeparator);
                }
                content.append(entry.getValue() == null ? "" : stringify(entry.getValue()));
            }
            return content.append(value(step, "suffix", "")).toString();
        }
    }

    private static final class DigestHandler extends BaseHandler {
        @Override public SecurityStepType type() { return SecurityStepType.DIGEST; }
        @Override public void validate(SecurityStepConfig step) {
            oneOf(step, "algorithm", "SHA256", "MD5", "SHA1", "SHA256", "SHA512", "SM3");
            oneOf(step, "outputEncoding", "HEX_LOWER", "HEX", "HEX_LOWER", "HEX_UPPER", "BASE64", "BASE64_URL");
            if (value(step, "secretRef", null) != null) {
                oneOf(step, "secretPlacement", "SUFFIX", "PREFIX", "SUFFIX");
            }
        }

        @Override
        public Object execute(SecurityExecutionContext context, SecurityStepConfig step) {
            String algorithm = value(step, "algorithm", "SHA256").toUpperCase(Locale.ROOT).replace("-", "");
            String jcaName = switch (algorithm) {
                case "MD5" -> "MD5";
                case "SHA1" -> "SHA-1";
                case "SHA256" -> "SHA-256";
                case "SHA512" -> "SHA-512";
                case "SM3" -> "SM3";
                default -> throw new IllegalArgumentException("不支持的摘要算法: " + algorithm);
            };
            try {
                String content = stringify(input(context, step));
                String secretRef = value(step, "secretRef", null);
                if (secretRef != null && !secretRef.isBlank()) {
                    String secret = context.resolveSecret(secretRef);
                    content = "PREFIX".equalsIgnoreCase(value(step, "secretPlacement", "SUFFIX"))
                            ? secret + content : content + secret;
                }
                MessageDigest digest = "SM3".equals(jcaName)
                        ? MessageDigest.getInstance(jcaName, BouncyCastleProvider.PROVIDER_NAME)
                        : MessageDigest.getInstance(jcaName);
                byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
                return encode(bytes, value(step, "outputEncoding", "HEX_LOWER"));
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException("摘要计算失败: " + algorithm, e);
            }
        }
    }

    private static final class HmacHandler extends BaseHandler {
        @Override public SecurityStepType type() { return SecurityStepType.HMAC; }
        @Override public void validate(SecurityStepConfig step) {
            required(step, "secretRef");
            oneOf(step, "algorithm", "HMAC_SHA256", "HMAC_SHA1", "HMACSHA1", "HMAC_SHA256",
                    "HMACSHA256", "HMAC_SHA512", "HMACSHA512");
            oneOf(step, "keyEncoding", "UTF8", "UTF8", "UTF_8", "HEX", "HEX_LOWER", "HEX_UPPER", "BASE64", "BASE64_URL");
            oneOf(step, "outputEncoding", "HEX_LOWER", "HEX", "HEX_LOWER", "HEX_UPPER", "BASE64", "BASE64_URL");
        }

        @Override
        public Object execute(SecurityExecutionContext context, SecurityStepConfig step) {
            String algorithm = value(step, "algorithm", "HMAC_SHA256").toUpperCase(Locale.ROOT);
            String jcaName = switch (algorithm) {
                case "HMAC_SHA1", "HMACSHA1" -> "HmacSHA1";
                case "HMAC_SHA256", "HMACSHA256" -> "HmacSHA256";
                case "HMAC_SHA512", "HMACSHA512" -> "HmacSHA512";
                default -> throw new IllegalArgumentException("不支持的HMAC算法: " + algorithm);
            };
            try {
                byte[] key = decodeKey(context.resolveSecret(value(step, "secretRef", null)),
                        value(step, "keyEncoding", "UTF8"));
                Mac mac = Mac.getInstance(jcaName);
                mac.init(new SecretKeySpec(key, jcaName));
                byte[] signed = mac.doFinal(stringify(input(context, step)).getBytes(StandardCharsets.UTF_8));
                return encode(signed, value(step, "outputEncoding", "HEX_LOWER"));
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException("HMAC计算失败: " + algorithm, e);
            }
        }
    }

    private static final class SignHandler extends BaseHandler {
        @Override public SecurityStepType type() { return SecurityStepType.SIGN; }
        @Override public void validate(SecurityStepConfig step) {
            required(step, "secretRef");
            oneOf(step, "algorithm", "RSA_SHA256", "RSA_SHA256");
            oneOf(step, "outputEncoding", "BASE64", "HEX", "HEX_LOWER", "HEX_UPPER", "BASE64", "BASE64_URL");
        }

        @Override
        public Object execute(SecurityExecutionContext context, SecurityStepConfig step) {
            String algorithm = value(step, "algorithm", "RSA_SHA256").toUpperCase(Locale.ROOT);
            if (!"RSA_SHA256".equals(algorithm)) {
                throw new IllegalArgumentException("不支持的签名算法: " + algorithm);
            }
            try {
                PrivateKey privateKey = rsaPrivateKey(context.resolveSecret(value(step, "secretRef", null)));
                Signature signature = Signature.getInstance("SHA256withRSA");
                signature.initSign(privateKey);
                signature.update(stringify(input(context, step)).getBytes(StandardCharsets.UTF_8));
                return encode(signature.sign(), value(step, "outputEncoding", "BASE64"));
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException("RSA签名失败", e);
            }
        }
    }

    private static final class VerifyHandler extends BaseHandler {
        @Override public SecurityStepType type() { return SecurityStepType.VERIFY; }
        @Override public void validate(SecurityStepConfig step) {
            required(step, "secretRef");
            required(step, "signatureFrom");
            oneOf(step, "algorithm", "RSA_SHA256", "RSA_SHA256", "HMAC_SHA1", "HMACSHA1",
                    "HMAC_SHA256", "HMACSHA256", "HMAC_SHA512", "HMACSHA512");
            oneOf(step, "signatureEncoding", "BASE64", "HEX", "HEX_LOWER", "HEX_UPPER", "BASE64", "BASE64_URL");
            oneOf(step, "keyEncoding", "UTF8", "UTF8", "UTF_8", "HEX", "HEX_LOWER", "HEX_UPPER", "BASE64", "BASE64_URL");
        }

        @Override
        public Object execute(SecurityExecutionContext context, SecurityStepConfig step) {
            String algorithm = value(step, "algorithm", "RSA_SHA256").toUpperCase(Locale.ROOT);
            try {
                byte[] suppliedSignature = decode(String.valueOf(context.resolveInput(
                                value(step, "signatureFrom", null))),
                        value(step, "signatureEncoding", "BASE64"));
                if (!"RSA_SHA256".equals(algorithm)) {
                    String jcaName = switch (algorithm) {
                        case "HMAC_SHA1", "HMACSHA1" -> "HmacSHA1";
                        case "HMAC_SHA256", "HMACSHA256" -> "HmacSHA256";
                        case "HMAC_SHA512", "HMACSHA512" -> "HmacSHA512";
                        default -> throw new IllegalArgumentException("不支持的验签算法: " + algorithm);
                    };
                    Mac mac = Mac.getInstance(jcaName);
                    mac.init(new SecretKeySpec(decodeKey(context.resolveSecret(
                            value(step, "secretRef", null)), value(step, "keyEncoding", "UTF8")), jcaName));
                    byte[] expected = mac.doFinal(stringify(input(context, step)).getBytes(StandardCharsets.UTF_8));
                    boolean verified = MessageDigest.isEqual(expected, suppliedSignature);
                    if (!verified && bool(step, "failOnInvalid", true)) {
                        throw new IllegalArgumentException("响应验签失败");
                    }
                    return verified;
                }
                PublicKey publicKey = rsaPublicKey(context.resolveSecret(value(step, "secretRef", null)));
                Signature verifier = Signature.getInstance("SHA256withRSA");
                verifier.initVerify(publicKey);
                verifier.update(stringify(input(context, step)).getBytes(StandardCharsets.UTF_8));
                boolean verified = verifier.verify(suppliedSignature);
                if (!verified && bool(step, "failOnInvalid", true)) {
                    throw new IllegalArgumentException("响应验签失败");
                }
                return verified;
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException("RSA验签失败", e);
            }
        }
    }

    private static final class EncryptHandler extends BaseHandler {
        private final boolean decrypt;

        private EncryptHandler(boolean decrypt) { this.decrypt = decrypt; }
        @Override public SecurityStepType type() { return decrypt ? SecurityStepType.DECRYPT : SecurityStepType.ENCRYPT; }
        @Override public void validate(SecurityStepConfig step) {
            required(step, "secretRef");
            String algorithm = oneOf(step, "algorithm", "AES_GCM", "AES_GCM", "AES_CBC", "RSA_OAEP", "SM4_CBC");
            if (decrypt) {
                oneOf(step, "inputEncoding", "BASE64", "HEX", "HEX_LOWER", "HEX_UPPER", "BASE64", "BASE64_URL");
            } else {
                oneOf(step, "outputEncoding", "BASE64", "HEX", "HEX_LOWER", "HEX_UPPER", "BASE64", "BASE64_URL");
            }
            if (!"RSA_OAEP".equals(algorithm)) {
                oneOf(step, "keyEncoding", "UTF8", "UTF8", "UTF_8", "HEX", "HEX_LOWER", "HEX_UPPER", "BASE64", "BASE64_URL");
                if (!bool(step, "prependIv", true) && (value(step, "iv", null) == null
                        || value(step, "iv", null).isBlank())) {
                    throw new IllegalArgumentException(algorithm + " 未携带IV时必须配置固定IV");
                }
            }
        }

        @Override
        public Object execute(SecurityExecutionContext context, SecurityStepConfig step) {
            String algorithm = value(step, "algorithm", "AES_GCM").toUpperCase(Locale.ROOT);
            try {
                return switch (algorithm) {
                    case "AES_GCM" -> aesGcm(context, step);
                    case "AES_CBC" -> blockCipher(context, step, "AES", "AES/CBC/PKCS5Padding", 16);
                    case "SM4_CBC" -> blockCipher(context, step, "SM4", "SM4/CBC/PKCS7Padding", 16);
                    case "RSA_OAEP" -> rsa(context, step);
                    default -> throw new IllegalArgumentException("不支持的加密算法: " + algorithm);
                };
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException((decrypt ? "解密" : "加密") + "失败: " + algorithm, e);
            }
        }

        private String aesGcm(SecurityExecutionContext context, SecurityStepConfig step)
                throws GeneralSecurityException {
            byte[] key = decodeKey(context.resolveSecret(value(step, "secretRef", null)),
                    value(step, "keyEncoding", "UTF8"));
            byte[] source = decrypt
                    ? decode(stringify(input(context, step)), value(step, "inputEncoding", "BASE64"))
                    : stringify(input(context, step)).getBytes(StandardCharsets.UTF_8);
            byte[] iv = resolveIv(step, source, 12);
            byte[] payload = decrypt && bool(step, "prependIv", true) ? slice(source, 12, source.length) : source;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(decrypt ? Cipher.DECRYPT_MODE : Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] result = cipher.doFinal(payload);
            if (decrypt) {
                return new String(result, StandardCharsets.UTF_8);
            }
            return encode(bool(step, "prependIv", true) ? concat(iv, result) : result,
                    value(step, "outputEncoding", "BASE64"));
        }

        private String blockCipher(SecurityExecutionContext context, SecurityStepConfig step,
                                   String keyAlgorithm, String transformation, int ivLength)
                throws GeneralSecurityException {
            byte[] key = decodeKey(context.resolveSecret(value(step, "secretRef", null)),
                    value(step, "keyEncoding", "UTF8"));
            byte[] source = decrypt
                    ? decode(stringify(input(context, step)), value(step, "inputEncoding", "BASE64"))
                    : stringify(input(context, step)).getBytes(StandardCharsets.UTF_8);
            byte[] iv = resolveIv(step, source, ivLength);
            byte[] payload = decrypt && bool(step, "prependIv", true) ? slice(source, ivLength, source.length) : source;
            Cipher cipher = transformation.startsWith("SM4")
                    ? Cipher.getInstance(transformation, BouncyCastleProvider.PROVIDER_NAME)
                    : Cipher.getInstance(transformation);
            cipher.init(decrypt ? Cipher.DECRYPT_MODE : Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, keyAlgorithm), new IvParameterSpec(iv));
            byte[] result = cipher.doFinal(payload);
            if (decrypt) {
                return new String(result, StandardCharsets.UTF_8);
            }
            return encode(bool(step, "prependIv", true) ? concat(iv, result) : result,
                    value(step, "outputEncoding", "BASE64"));
        }

        private String rsa(SecurityExecutionContext context, SecurityStepConfig step) throws GeneralSecurityException {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            OAEPParameterSpec parameters = new OAEPParameterSpec("SHA-256", "MGF1",
                    MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
            if (decrypt) {
                cipher.init(Cipher.DECRYPT_MODE,
                        rsaPrivateKey(context.resolveSecret(value(step, "secretRef", null))), parameters);
                byte[] decoded = decode(stringify(input(context, step)), value(step, "inputEncoding", "BASE64"));
                return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
            }
            cipher.init(Cipher.ENCRYPT_MODE,
                    rsaPublicKey(context.resolveSecret(value(step, "secretRef", null))), parameters);
            return encode(cipher.doFinal(stringify(input(context, step)).getBytes(StandardCharsets.UTF_8)),
                    value(step, "outputEncoding", "BASE64"));
        }

        private byte[] resolveIv(SecurityStepConfig step, byte[] source, int length) {
            String configuredIv = value(step, "iv", null);
            if (configuredIv != null && !configuredIv.isBlank()) {
                byte[] iv = decode(configuredIv, value(step, "ivEncoding", "UTF8"));
                if (iv.length != length) {
                    throw new IllegalArgumentException("IV长度必须为" + length + "字节");
                }
                return iv;
            }
            if (decrypt) {
                if (!bool(step, "prependIv", true) || source.length <= length) {
                    throw new IllegalArgumentException("解密配置缺少IV");
                }
                return slice(source, 0, length);
            }
            byte[] iv = new byte[length];
            SECURE_RANDOM.nextBytes(iv);
            return iv;
        }
    }

    private static final class CodecHandler extends BaseHandler {
        private final boolean decode;
        private CodecHandler(boolean decode) { this.decode = decode; }
        @Override public SecurityStepType type() { return decode ? SecurityStepType.DECODE : SecurityStepType.ENCODE; }
        @Override public void validate(SecurityStepConfig step) {
            oneOf(step, "encoding", "BASE64", "HEX", "HEX_LOWER", "HEX_UPPER", "BASE64", "BASE64_URL");
        }

        @Override
        public Object execute(SecurityExecutionContext context, SecurityStepConfig step) {
            String encoding = value(step, "encoding", "BASE64");
            if (decode) {
                return new String(DefaultSecurityStepHandlers.decode(stringify(input(context, step)), encoding),
                        StandardCharsets.UTF_8);
            }
            return DefaultSecurityStepHandlers.encode(stringify(input(context, step)).getBytes(StandardCharsets.UTF_8),
                    encoding);
        }
    }

    private static final class InjectHandler extends BaseHandler {
        @Override public SecurityStepType type() { return SecurityStepType.INJECT; }
        @Override public void validate(SecurityStepConfig step) {
            oneOf(step, "location", "PARAM", "PARAM", "PARAMS", "BODY_FIELD", "HEADER", "QUERY", "BODY");
        }

        @Override
        public Object execute(SecurityExecutionContext context, SecurityStepConfig step) {
            Object source = input(context, step);
            String location = value(step, "location", "PARAM");
            String fieldName = value(step, "fieldName", null);
            if (!"BODY".equalsIgnoreCase(location) && (fieldName == null || fieldName.isBlank())) {
                throw new IllegalArgumentException("INJECT 非BODY位置必须配置fieldName");
            }
            inject(context, location, fieldName, source);
            return source;
        }
    }

    private static final class RemoveFieldHandler extends BaseHandler {
        @Override public SecurityStepType type() { return SecurityStepType.REMOVE_FIELD; }
        @Override public void validate(SecurityStepConfig step) {
            required(step, "fieldName");
            oneOf(step, "location", "PARAM", "PARAM", "PARAMS", "BODY_FIELD", "HEADER", "QUERY");
        }

        @Override
        public Object execute(SecurityExecutionContext context, SecurityStepConfig step) {
            String location = value(step, "location", "PARAM").toUpperCase(Locale.ROOT);
            String fieldName = value(step, "fieldName", null);
            return switch (location) {
                case "PARAM", "PARAMS", "BODY_FIELD" -> context.getParams().remove(fieldName);
                case "HEADER" -> context.getHeaders().remove(fieldName);
                case "QUERY" -> context.getQuery().remove(fieldName);
                default -> throw new IllegalArgumentException("不支持的移除位置: " + location);
            };
        }
    }

    private static void inject(SecurityExecutionContext context, String location, String fieldName, Object source) {
        switch (location.toUpperCase(Locale.ROOT)) {
            case "PARAM", "PARAMS", "BODY_FIELD" -> context.getParams().put(fieldName, source);
            case "HEADER" -> context.getHeaders().put(fieldName, stringify(source));
            case "QUERY" -> context.getQuery().put(fieldName, stringify(source));
            case "BODY" -> context.setBody(stringify(source));
            default -> throw new IllegalArgumentException("不支持的注入位置: " + location);
        }
    }

    private static byte[] decodeKey(String value, String encoding) {
        return decode(value, encoding);
    }

    private static String encode(byte[] value, String encoding) {
        return switch (encoding.toUpperCase(Locale.ROOT)) {
            case "HEX", "HEX_LOWER" -> HexFormat.of().formatHex(value);
            case "HEX_UPPER" -> HexFormat.of().withUpperCase().formatHex(value);
            case "BASE64" -> Base64.getEncoder().encodeToString(value);
            case "BASE64_URL" -> Base64.getUrlEncoder().withoutPadding().encodeToString(value);
            case "UTF8", "UTF_8" -> new String(value, StandardCharsets.UTF_8);
            default -> throw new IllegalArgumentException("不支持的编码: " + encoding);
        };
    }

    private static byte[] decode(String value, String encoding) {
        return switch (encoding.toUpperCase(Locale.ROOT)) {
            case "HEX", "HEX_LOWER", "HEX_UPPER" -> HexFormat.of().parseHex(value);
            case "BASE64" -> Base64.getDecoder().decode(value);
            case "BASE64_URL" -> Base64.getUrlDecoder().decode(value);
            case "UTF8", "UTF_8" -> value.getBytes(StandardCharsets.UTF_8);
            default -> throw new IllegalArgumentException("不支持的编码: " + encoding);
        };
    }

    private static PrivateKey rsaPrivateKey(String pem) throws GeneralSecurityException {
        byte[] key = Base64.getDecoder().decode(stripPem(pem));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key));
    }

    private static PublicKey rsaPublicKey(String pem) throws GeneralSecurityException {
        byte[] key = Base64.getDecoder().decode(stripPem(pem));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key));
    }

    private static String stripPem(String value) {
        return value.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                return OBJECT_MAPPER.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("安全步骤输入无法序列化", e);
            }
        }
        return String.valueOf(value);
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String string && !string.isBlank()) {
            return List.of(string.split(","));
        }
        return List.of();
    }

    private static String randomHex(int length) {
        int byteLength = Math.max(1, (length + 1) / 2);
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes).substring(0, Math.min(length, byteLength * 2));
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static byte[] slice(byte[] source, int from, int to) {
        byte[] result = new byte[to - from];
        System.arraycopy(source, from, result, 0, result.length);
        return result;
    }
}
