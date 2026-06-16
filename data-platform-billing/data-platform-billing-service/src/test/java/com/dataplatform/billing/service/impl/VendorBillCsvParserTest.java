package com.dataplatform.billing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class VendorBillCsvParserTest {

    @Test
    void parsesCsvAndSkipsDuplicateVendorDateRows() {
        String csv = """
            billingDate,vendorId,vendorName,vendorCount,vendorAmount
            2026-05-16,10,Acme Data,100,30.00
            2026-05-16,10,Acme Data Duplicate,101,31.00
            2026-05-16,11,Backup Data,50,15.50
            """;

        List<VendorBillCsvParser.VendorBillRow> rows = VendorBillCsvParser.parse(csv);

        assertEquals(2, rows.size());
        assertEquals(LocalDate.of(2026, 5, 16), rows.get(0).billingDate());
        assertEquals(10L, rows.get(0).vendorId());
        assertEquals("Acme Data", rows.get(0).vendorName());
        assertEquals(100L, rows.get(0).vendorCount());
        assertEquals(new BigDecimal("30.00"), rows.get(0).vendorAmount());
    }

    @Test
    void classifiesDiffRateByWarningAndErrorThresholds() {
        assertEquals("matched", VendorBillCsvParser.classifyStatus(new BigDecimal("0.010000")));
        assertEquals("diff_warning", VendorBillCsvParser.classifyStatus(new BigDecimal("0.020000")));
        assertEquals("diff_error", VendorBillCsvParser.classifyStatus(new BigDecimal("0.060000")));
    }

    @Test
    void calculatesDiffRateFromCountAndAmountDifferences() {
        BigDecimal rate = VendorBillCsvParser.calculateDiffRate(
            0L,
            100L,
            100L,
            new BigDecimal("10.00"),
            new BigDecimal("110.00"),
            new BigDecimal("100.00")
        );

        assertEquals(new BigDecimal("0.090909"), rate);
        assertEquals("diff_error", VendorBillCsvParser.classifyStatus(rate));
    }
}
