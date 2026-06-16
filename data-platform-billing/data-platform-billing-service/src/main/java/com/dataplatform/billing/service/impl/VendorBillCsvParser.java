package com.dataplatform.billing.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class VendorBillCsvParser {

    private static final BigDecimal DIFF_RATE_WARNING = new BigDecimal("0.01");
    private static final BigDecimal DIFF_RATE_ERROR = new BigDecimal("0.05");

    private VendorBillCsvParser() {
    }

    static List<VendorBillRow> parse(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return List.of();
        }
        String[] lines = csv.split("\\R");
        if (lines.length <= 1) {
            return List.of();
        }

        Map<String, Integer> headers = parseHeaders(lines[0]);
        Map<String, VendorBillRow> deduped = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] columns = line.split(",", -1);
            VendorBillRow row = new VendorBillRow(
                    LocalDate.parse(value(columns, headers, "billingdate")),
                    Long.parseLong(value(columns, headers, "vendorid")),
                    value(columns, headers, "vendorname"),
                    Long.parseLong(value(columns, headers, "vendorcount")),
                    new BigDecimal(value(columns, headers, "vendoramount"))
            );
            deduped.putIfAbsent(row.billingDate() + ":" + row.vendorId(), row);
        }
        return new ArrayList<>(deduped.values());
    }

    static String classifyStatus(BigDecimal diffRate) {
        BigDecimal normalized = diffRate == null ? BigDecimal.ZERO : diffRate.abs();
        if (normalized.compareTo(DIFF_RATE_WARNING) <= 0) {
            return "matched";
        }
        if (normalized.compareTo(DIFF_RATE_ERROR) <= 0) {
            return "diff_warning";
        }
        return "diff_error";
    }

    static BigDecimal calculateDiffRate(Long diffCount, Long platformCount, Long vendorCount,
                                        BigDecimal diffAmount, BigDecimal platformAmount,
                                        BigDecimal vendorAmount) {
        BigDecimal countRate = calculateRate(
                BigDecimal.valueOf(diffCount == null ? 0L : diffCount),
                BigDecimal.valueOf(maxAbs(platformCount, vendorCount)));
        BigDecimal amountRate = calculateRate(
                diffAmount == null ? BigDecimal.ZERO : diffAmount,
                maxAbs(platformAmount, vendorAmount));
        return countRate.max(amountRate);
    }

    private static BigDecimal calculateRate(BigDecimal diff, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return diff.abs().divide(denominator.abs(), 6, RoundingMode.HALF_UP);
    }

    private static long maxAbs(Long first, Long second) {
        return Math.max(Math.abs(first == null ? 0L : first), Math.abs(second == null ? 0L : second));
    }

    private static BigDecimal maxAbs(BigDecimal first, BigDecimal second) {
        BigDecimal left = first == null ? BigDecimal.ZERO : first.abs();
        BigDecimal right = second == null ? BigDecimal.ZERO : second.abs();
        return left.max(right);
    }

    private static Map<String, Integer> parseHeaders(String headerLine) {
        String[] names = headerLine.split(",", -1);
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            headers.put(normalize(names[i]), i);
        }
        return headers;
    }

    private static String value(String[] columns, Map<String, Integer> headers, String name) {
        Integer index = headers.get(name);
        if (index == null || index >= columns.length) {
            throw new IllegalArgumentException("CSV缺少字段: " + name);
        }
        String value = columns[index].trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("CSV字段不能为空: " + name);
        }
        return value;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().replace("_", "").toLowerCase(Locale.ROOT);
    }

    record VendorBillRow(LocalDate billingDate, Long vendorId, String vendorName,
                         Long vendorCount, BigDecimal vendorAmount) {
    }
}
