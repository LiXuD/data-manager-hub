package com.dataplatform.access.caller.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.vo.ApiKeyResponse;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ApiKeyResponseTest {

    @Test
    void revealsOnlyTheCreatedKeyAndMasksLaterViews() throws Exception {
        ApiKey source = new ApiKey();
        source.setApiKey("dp_0123456789abcdef");
        source.setApiSecret("secret-must-never-leak");
        source.setStatus(ApiKeyStatus.ACTIVE);

        ApiKeyResponse created = ApiKeyResponse.created(source);
        ApiKeyResponse view = ApiKeyResponse.view(source);

        assertEquals("dp_0123456789abcdef", created.apiKey());
        assertEquals("dp_••••cdef", view.apiKey());
        String json = new ObjectMapper().writeValueAsString(created);
        assertFalse(json.contains("secret-must-never-leak"));
    }
}
