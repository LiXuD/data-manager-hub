package com.dataplatform.billing.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.billing.entity.BillingEvent;
import com.dataplatform.billing.model.BillingReversalCommand;
import com.dataplatform.billing.service.BillingContractReviewService;
import com.dataplatform.billing.service.BillingEventQueryService;
import com.dataplatform.billing.service.BillingPlanService;
import com.dataplatform.billing.service.BillingRecurringChargeService;
import com.dataplatform.billing.service.BillingReversalService;
import com.dataplatform.billing.service.BillingSimulationService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.common.util.UserContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class BillingPlanControllerAuthorizationTest {

    private BillingEventQueryService eventQueryService;
    private BillingReversalService reversalService;
    private BillingPlanController controller;

    @BeforeEach
    void setUp() {
        eventQueryService = mock(BillingEventQueryService.class);
        reversalService = mock(BillingReversalService.class);
        controller = new BillingPlanController(
                mock(BillingPlanService.class),
                mock(BillingSimulationService.class),
                mock(BillingRecurringChargeService.class),
                eventQueryService,
                reversalService,
                mock(BillingContractReviewService.class));
    }

    @Test
    void scopesEventListAndStatsToTheCurrentTenant() {
        when(eventQueryService.page(7L, null, null, null, null, null, null, 1, 10))
                .thenReturn(new Page<>(1, 10));
        when(eventQueryService.stats(7L, null, null, null, null, null))
                .thenReturn(Map.of("eventCount", 0));

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("billing:view")).thenReturn(true);
            userContext.when(() -> UserContext.hasPermission("billing:view-all")).thenReturn(false);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);

            PageResult<BillingEvent> page = controller.events(
                    null, null, null, null, null, null, null, 1, 10);
            Result<Map<String, Object>> stats = controller.eventStats(
                    null, null, null, null, null, null);

            assertEquals(200, page.getCode());
            assertEquals(200, stats.getCode());
            verify(eventQueryService).page(7L, null, null, null, null, null, null, 1, 10);
            verify(eventQueryService).stats(7L, null, null, null, null, null);
        }
    }

    @Test
    void rejectsCrossTenantEventReadsBeforeQuerying() {
        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("billing:view")).thenReturn(true);
            userContext.when(() -> UserContext.hasPermission("billing:view-all")).thenReturn(false);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);

            assertEquals(403, controller.events(
                    8L, null, null, null, null, null, null, 1, 10).getCode());
            assertEquals(403, controller.eventStats(
                    8L, null, null, null, null, null).getCode());

            verifyNoInteractions(eventQueryService);
        }
    }

    @Test
    void allowsViewAllWithoutTheBaseViewPermissionAndKeepsRequestedScope() {
        when(eventQueryService.page(null, 3L, 4L, "VENDOR_PAYABLE", "POSTED",
                null, null, 1, 10)).thenReturn(new Page<>(1, 10));

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("billing:view")).thenReturn(false);
            userContext.when(() -> UserContext.hasPermission("billing:view-all")).thenReturn(true);

            PageResult<BillingEvent> result = controller.events(
                    null, 3L, 4L, "VENDOR_PAYABLE", "POSTED", null, null, 1, 10);

            assertEquals(200, result.getCode());
            verify(eventQueryService).page(null, 3L, 4L, "VENDOR_PAYABLE", "POSTED",
                    null, null, 1, 10);
        }
    }

    @Test
    void rejectsCrossTenantReverseBeforeMutatingTheLedger() {
        BillingEvent event = new BillingEvent();
        event.setId(11L);
        event.setTenantId(8L);
        when(eventQueryService.getById(11L)).thenReturn(event);
        BillingReversalCommand command = new BillingReversalCommand();
        command.setRequestId("reverse-11");
        command.setReason("duplicate");

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("billing:reverse")).thenReturn(true);
            userContext.when(() -> UserContext.hasPermission("billing:view-all")).thenReturn(false);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);

            Result<BillingEvent> result = controller.reverse(11L, command);

            assertEquals(403, result.getCode());
            verifyNoInteractions(reversalService);
        }
    }

    @Test
    void reversesAnEventInTheCurrentTenant() {
        BillingEvent event = new BillingEvent();
        event.setId(11L);
        event.setTenantId(7L);
        BillingReversalCommand command = new BillingReversalCommand();
        command.setRequestId("reverse-11");
        command.setReason("duplicate");
        BillingEvent reversal = new BillingEvent();
        reversal.setId(12L);
        when(eventQueryService.getById(11L)).thenReturn(event);
        when(reversalService.reverse(11L, command)).thenReturn(reversal);

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("billing:reverse")).thenReturn(true);
            userContext.when(() -> UserContext.hasPermission("billing:view-all")).thenReturn(false);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);

            Result<BillingEvent> result = controller.reverse(11L, command);

            assertEquals(200, result.getCode());
            assertEquals(reversal, result.getData());
            verify(reversalService).reverse(11L, command);
        }
    }
}
