package com.dataplatform.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.billing.api.dto.BillingChargeReqDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class BillingChargeReqDTOCompatibilityTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsLegacyPayloadsAndSerializesOptionalIntegrityFacts() throws Exception {
        BillingChargeReqDTO legacy = mapper.readValue(
                "{\"requestId\":\"legacy-request\",\"success\":true}", BillingChargeReqDTO.class);
        assertEquals("legacy-request", legacy.getRequestId());
        assertNull(legacy.getHashAlgorithm());
        assertNull(legacy.getIntegrityHash());

        legacy.setHashAlgorithm("V2_EMBEDDED");
        legacy.setIntegrityHash("a".repeat(64));
        String json = mapper.writeValueAsString(legacy);
        assertTrue(json.contains("\"hashAlgorithm\":\"V2_EMBEDDED\""));
        assertTrue(json.contains("\"integrityHash\":\"" + "a".repeat(64) + "\""));
    }
}
