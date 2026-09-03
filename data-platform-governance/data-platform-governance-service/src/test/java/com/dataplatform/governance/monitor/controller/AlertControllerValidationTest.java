package com.dataplatform.governance.monitor.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dataplatform.common.result.Result;
import com.dataplatform.governance.monitor.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

class AlertControllerValidationTest {

    private AlertService alertService;
    private AlertController controller;

    @BeforeEach
    void setUp() {
        alertService = mock(AlertService.class);
        controller = new AlertController();
        ReflectionTestUtils.setField(controller, "alertService", alertService);
    }

    @Test
    void rejectsNullRuleBody() {
        ResponseEntity<?> response = controller.createRule(null);

        assertEquals(400, ((Result<?>) response.getBody()).getCode());
        verifyNoInteractions(alertService);
    }

    @Test
    void rejectsNullStatusBodyBeforeLoadingRule() {
        ResponseEntity<?> response = controller.updateRuleStatus(1L, null);

        assertEquals(400, ((Result<?>) response.getBody()).getCode());
        verifyNoInteractions(alertService);
    }

    @Test
    void rejectsNullResolutionBodyBeforeLoadingRecord() {
        ResponseEntity<?> response = controller.resolveRecord(1L, null);

        assertEquals(400, ((Result<?>) response.getBody()).getCode());
        verifyNoInteractions(alertService);
    }

    @Test
    void rejectsNullUpdateBodyBeforeLoadingRule() {
        ResponseEntity<?> response = controller.updateRule(1L, null);

        assertEquals(400, ((Result<?>) response.getBody()).getCode());
        verifyNoInteractions(alertService);
    }
}
