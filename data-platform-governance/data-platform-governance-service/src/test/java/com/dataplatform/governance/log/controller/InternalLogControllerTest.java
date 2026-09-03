package com.dataplatform.governance.log.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dataplatform.common.result.Result;
import com.dataplatform.governance.log.entity.OperationLog;
import com.dataplatform.governance.log.service.LogService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InternalLogControllerTest {

    private LogService logService;
    private InternalLogController controller;

    @BeforeEach
    void setUp() {
        logService = mock(LogService.class);
        controller = new InternalLogController();
        ReflectionTestUtils.setField(controller, "logService", logService);
    }

    @Test
    void rejectsNullBodyAsBadRequest() {
        Result<Void> result = controller.saveLog(null);

        assertEquals(400, result.getCode());
        verifyNoInteractions(logService);
    }

    @Test
    void rejectsMalformedNumericFieldsAsBadRequest() {
        Result<Void> result = controller.saveLog(Map.of("userId", "not-a-number"));

        assertEquals(400, result.getCode());
        verifyNoInteractions(logService);
    }

    @Test
    void acceptsScalarLogPayload() {
        Result<Void> result = controller.saveLog(Map.of(
                "userId", 7,
                "username", "alice",
                "module", "access",
                "operation", "query",
                "duration", 12,
                "status", "success"));

        assertEquals(200, result.getCode());
        verify(logService).saveLog(any(OperationLog.class));
    }
}
