package com.dataplatform.billing.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.billing.entity.BillingDaily;
import com.dataplatform.billing.service.BillingService;
import com.dataplatform.billing.service.ReconciliationService;
import com.dataplatform.common.util.UserContext;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class BillingControllerAuthorizationTest {

    private BillingService billingService;
    private ReconciliationService reconciliationService;
    private BillingController controller;

    @BeforeEach
    void setUp() {
        billingService = mock(BillingService.class);
        reconciliationService = mock(ReconciliationService.class);
        controller = new BillingController();
        ReflectionTestUtils.setField(controller, "billingService", billingService);
        ReflectionTestUtils.setField(controller, "reconciliationService", reconciliationService);
    }

    @Test
    void rejectsBillingReadsWithoutViewPermission() {
        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("billing:view")).thenReturn(false);

            assertEquals(403, controller.list(null, null, null, null, 1, 10).getCode());
            assertEquals(HttpStatus.FORBIDDEN, controller.getById(1L).getStatusCode());
            assertEquals(403, controller.stats(null, null, null).getCode());
            assertEquals(HttpStatus.FORBIDDEN, controller.export(null, null, null, null).getStatusCode());

            verifyNoInteractions(billingService);
        }
    }

    @Test
    void scopesListAndExportToCurrentTenant() {
        Page<BillingDaily> page = new Page<>(1, 10);
        when(billingService.pageQuery(7L, 3L, null, null, 1, 10)).thenReturn(page);
        when(billingService.export(7L, 3L, null, null)).thenReturn(new byte[0]);

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("billing:view")).thenReturn(true);
            userContext.when(() -> UserContext.hasPermission("billing:view-all")).thenReturn(false);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);

            assertEquals(200, controller.list(null, 3L, null, null, 1, 10).getCode());
            assertEquals(HttpStatus.OK, controller.export(null, 3L, null, null).getStatusCode());

            verify(billingService).pageQuery(7L, 3L, null, null, 1, 10);
            verify(billingService).export(7L, 3L, null, null);
        }
    }

    @Test
    void rejectsCrossTenantReadsBeforeQueryingBillingRows() {
        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("billing:view")).thenReturn(true);
            userContext.when(() -> UserContext.hasPermission("billing:view-all")).thenReturn(false);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);

            assertEquals(403, controller.list(8L, null, null, null, 1, 10).getCode());
            assertEquals(403, controller.stats(8L, null, null).getCode());
            assertEquals(HttpStatus.FORBIDDEN, controller.export(8L, null, null, null).getStatusCode());

            verifyNoInteractions(billingService);
        }
    }

    @Test
    void permitsViewAllUsersToQueryAcrossTenants() {
        Page<BillingDaily> page = new Page<>(1, 10);
        when(billingService.pageQuery(null, null, null, null, 1, 10)).thenReturn(page);

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("billing:view")).thenReturn(true);
            userContext.when(() -> UserContext.hasPermission("billing:view-all")).thenReturn(true);

            assertEquals(200, controller.list(null, null, null, null, 1, 10).getCode());
            verify(billingService).pageQuery(null, null, null, null, 1, 10);
        }
    }

    @Test
    void rejectsBillFromAnotherTenantAfterLookup() {
        BillingDaily billing = new BillingDaily();
        billing.setTenantId(8L);
        when(billingService.getById(1L)).thenReturn(billing);

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("billing:view")).thenReturn(true);
            userContext.when(() -> UserContext.hasPermission("billing:view-all")).thenReturn(false);
            userContext.when(UserContext::getCurrentTenantId).thenReturn(7L);

            assertEquals(HttpStatus.FORBIDDEN, controller.getById(1L).getStatusCode());
        }
    }

    @Test
    void rejectsEveryReconciliationOperationWithoutReconcilePermission() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bill.csv", "text/csv", new byte[0]);
        LocalDate billingDate = LocalDate.of(2026, 7, 21);

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("billing:reconcile")).thenReturn(false);

            assertEquals(403, controller.importVendorBill(file).getCode());
            assertEquals(403, controller.runReconciliation(null, billingDate).getCode());
            assertEquals(403, controller.listReconciliation(null, null, null, 1, 10).getCode());
            assertEquals(403, controller.listReconciliationDiffs(null, null, null).getCode());

            verifyNoInteractions(reconciliationService);
        }
    }

    @Test
    void permitsReconciliationWithDedicatedPermission() {
        LocalDate billingDate = LocalDate.of(2026, 7, 21);

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("billing:reconcile")).thenReturn(true);

            assertEquals(200, controller.runReconciliation(3L, billingDate).getCode());
            verify(reconciliationService).reconcile(3L, billingDate);
        }
    }
}
