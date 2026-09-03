package com.dataplatform.access.caller.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.api.Result;
import java.util.List;
import org.junit.jupiter.api.Test;

class CallerInternalControllerTest {

    @Test
    void validatesOnlyRequestedActiveCallersFromTheTenant() {
        CallerService callerService = mock(CallerService.class);
        CallerInfo active = caller(11L);
        CallerInfo other = caller(12L);
        when(callerService.listByTenant(7L)).thenReturn(List.of(active, other));
        CallerInternalController controller = new CallerInternalController(callerService);

        Result<List<Long>> result = controller.validate(7L, List.of(11L, 99L));

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsExactly(11L);
    }

    @Test
    void rejectsMalformedValidationRequestBeforeQuerying() {
        CallerService callerService = mock(CallerService.class);
        CallerInternalController controller = new CallerInternalController(callerService);

        Result<List<Long>> result = controller.validate(7L, java.util.Arrays.asList(11L, null));

        assertThat(result.getCode()).isEqualTo(400);
        verifyNoInteractions(callerService);
    }

    private CallerInfo caller(Long id) {
        CallerInfo caller = new CallerInfo();
        caller.setId(id);
        return caller;
    }
}
