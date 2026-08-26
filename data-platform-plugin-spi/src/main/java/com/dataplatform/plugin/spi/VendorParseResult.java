package com.dataplatform.plugin.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable business facts produced by a vendor response parser. */
public final class VendorParseResult {

    public static final int MAX_SAFE_MESSAGE_LENGTH = 512;
    public static final int MAX_VENDOR_BUSINESS_CODE_LENGTH = 128;

    private final BusinessStatus businessStatus;
    private final Map<String, Object> data;
    private final String vendorBusinessCode;
    private final BillingSignal billingSignal;
    private final CacheSignal cacheSignal;
    private final String safeMessage;

    private VendorParseResult(
            BusinessStatus businessStatus,
            Map<String, ?> data,
            String vendorBusinessCode,
            BillingSignal billingSignal,
            CacheSignal cacheSignal,
            String safeMessage) {
        this.businessStatus = requireFinalStatus(businessStatus);
        this.data = immutableMap(Objects.requireNonNull(data, "data"));
        this.vendorBusinessCode = truncateOptional(vendorBusinessCode, MAX_VENDOR_BUSINESS_CODE_LENGTH);
        this.billingSignal = Objects.requireNonNull(billingSignal, "billingSignal");
        this.cacheSignal = Objects.requireNonNull(cacheSignal, "cacheSignal");
        this.safeMessage = validateSafeMessage(safeMessage);
        validateSignals(this.businessStatus, this.billingSignal, this.cacheSignal);
    }

    public static VendorParseResult success(Map<String, ?> data) {
        return success(data, null, BillingSignal.ELIGIBLE, CacheSignal.CACHEABLE, null);
    }

    public static VendorParseResult success(
            Map<String, ?> data,
            String vendorBusinessCode,
            BillingSignal billingSignal,
            CacheSignal cacheSignal,
            String safeMessage) {
        return new VendorParseResult(BusinessStatus.SUCCESS, data, vendorBusinessCode,
                billingSignal, cacheSignal, safeMessage);
    }

    public static VendorParseResult rejected(
            Map<String, ?> data,
            String vendorBusinessCode,
            String safeMessage) {
        return rejected(data, vendorBusinessCode, BillingSignal.INELIGIBLE,
                CacheSignal.NOT_CACHEABLE, safeMessage);
    }

    public static VendorParseResult rejected(
            Map<String, ?> data,
            String vendorBusinessCode,
            BillingSignal billingSignal,
            CacheSignal cacheSignal,
            String safeMessage) {
        return new VendorParseResult(BusinessStatus.REJECTED, data, vendorBusinessCode,
                billingSignal, cacheSignal, safeMessage);
    }

    public static VendorParseResult unknown(
            Map<String, ?> data,
            String vendorBusinessCode,
            String safeMessage) {
        return unknown(data, vendorBusinessCode, BillingSignal.UNKNOWN, CacheSignal.UNKNOWN, safeMessage);
    }

    public static VendorParseResult unknown(
            Map<String, ?> data,
            String vendorBusinessCode,
            BillingSignal billingSignal,
            CacheSignal cacheSignal,
            String safeMessage) {
        return new VendorParseResult(BusinessStatus.UNKNOWN, data, vendorBusinessCode,
                billingSignal, cacheSignal, safeMessage);
    }

    public BusinessStatus businessStatus() {
        return businessStatus;
    }

    /** Returns a deeply isolated, unmodifiable JSON-compatible structure. */
    public Map<String, Object> data() {
        return immutableMap(data);
    }

    public String vendorBusinessCode() {
        return vendorBusinessCode;
    }

    public BillingSignal billingSignal() {
        return billingSignal;
    }

    public CacheSignal cacheSignal() {
        return cacheSignal;
    }

    public String safeMessage() {
        return safeMessage;
    }

    private static BusinessStatus requireFinalStatus(BusinessStatus status) {
        Objects.requireNonNull(status, "businessStatus");
        if (status == BusinessStatus.NOT_EVALUATED) {
            throw new IllegalArgumentException("businessStatus must be final");
        }
        return status;
    }

    private static void validateSignals(
            BusinessStatus status,
            BillingSignal billingSignal,
            CacheSignal cacheSignal) {
        if (status == BusinessStatus.SUCCESS && billingSignal == BillingSignal.INELIGIBLE) {
            throw new IllegalArgumentException("successful result cannot be billing-ineligible");
        }
        if (status == BusinessStatus.REJECTED
                && (billingSignal != BillingSignal.INELIGIBLE
                || cacheSignal != CacheSignal.NOT_CACHEABLE)) {
            throw new IllegalArgumentException("rejected result must be ineligible and not cacheable");
        }
        if (status == BusinessStatus.UNKNOWN
                && (billingSignal != BillingSignal.UNKNOWN || cacheSignal != CacheSignal.UNKNOWN)) {
            throw new IllegalArgumentException("unknown result must use unknown signals");
        }
    }

    private static String validateSafeMessage(String value) {
        if (value != null && value.length() > MAX_SAFE_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("safeMessage exceeds 512 characters");
        }
        return optionalText(value);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncateOptional(String value, int maximumLength) {
        String text = optionalText(value);
        return text == null || text.length() <= maximumLength
                ? text : text.substring(0, maximumLength);
    }

    private static Map<String, Object> immutableMap(Map<String, ?> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null) {
                throw new IllegalArgumentException("data keys cannot be null");
            }
            copy.put(key, immutableValue(value));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode.deepCopy();
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (!(key instanceof String text)) {
                    throw new IllegalArgumentException("nested data keys must be strings");
                }
                copy.put(text, immutableValue(nested));
            });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> copy = new ArrayList<>();
            iterable.forEach(item -> copy.add(immutableValue(item)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof byte[] bytes) {
            return bytes.clone();
        }
        throw new IllegalArgumentException("data contains a non-structured value");
    }
}
