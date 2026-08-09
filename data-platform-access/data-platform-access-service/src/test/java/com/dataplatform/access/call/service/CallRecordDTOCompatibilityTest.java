package com.dataplatform.access.call.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dataplatform.access.call.api.dto.CallRecordDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CallRecordDTOCompatibilityTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keepsLegacyJsonCompatibleAndExposesNewTraceFields() throws Exception {
        CallRecordDTO legacy = mapper.readValue(
                "{\"requestId\":\"legacy-record\",\"success\":true}", CallRecordDTO.class);
        assertEquals("legacy-record", legacy.getRequestId());
        assertNull(legacy.getHashAlgorithm());
        assertNull(legacy.getIntegrityHash());

        legacy.setHashAlgorithm("V1_DERIVED");
        legacy.setIntegrityHash("b".repeat(64));
        String json = mapper.writeValueAsString(legacy);
        assertTrue(json.contains("\"hashAlgorithm\":\"V1_DERIVED\""));
        assertTrue(json.contains("\"integrityHash\":\"" + "b".repeat(64) + "\""));
    }
}
