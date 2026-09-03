package com.dataplatform.governance.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dataplatform.governance.api.dto.AlertRecordCreateDTO;
import com.dataplatform.governance.monitor.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernanceInternalControllerValidationTest {

    private AlertService alertService;
    private GovernanceInternalController controller;

    @BeforeEach
    void setUp() {
        alertService = mock(AlertService.class);
        controller = new GovernanceInternalController(alertService);
    }

    @Test
    void rejectsNullRequestWithoutCallingService() {
        assertEquals(400, controller.createAlertRecord(null).getCode());
        verifyNoInteractions(alertService);
    }

    @Test
    void rejectsMissingRequiredFieldsWithoutCallingService() {
        AlertRecordCreateDTO dto = new AlertRecordCreateDTO();

        assertEquals(400, controller.createAlertRecord(dto).getCode());
        verifyNoInteractions(alertService);
    }

    @Test
    void acceptsGlobalReconciliationAlertWithoutTenantId() {
        AlertRecordCreateDTO dto = new AlertRecordCreateDTO();
        dto.setAlertType("billing_reconciliation");
        dto.setAlertTitle("计费对账差异");
        dto.setLevel("warning");

        assertEquals(200, controller.createAlertRecord(dto).getCode());
    }
}
