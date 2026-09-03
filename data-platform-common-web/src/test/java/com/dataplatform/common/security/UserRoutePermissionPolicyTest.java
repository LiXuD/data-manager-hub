package com.dataplatform.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRoutePermissionPolicyTest {

    @Test
    void mapsReadAndWriteActionsToSeparateCapabilities() {
        assertEquals("vendor:view",
                UserRoutePermissionPolicy.requiredPermission("GET", "/vendor/42"));
        assertEquals("vendor:add",
                UserRoutePermissionPolicy.requiredPermission("POST", "/vendor"));
        assertEquals("vendor:edit",
                UserRoutePermissionPolicy.requiredPermission("PATCH", "/vendor/42/status"));
        assertEquals("vendor:delete",
                UserRoutePermissionPolicy.requiredPermission("DELETE", "/vendor/42"));
        assertEquals("config:view",
                UserRoutePermissionPolicy.requiredPermission("GET", "/config/key/example"));
        assertEquals("config:edit",
                UserRoutePermissionPolicy.requiredPermission("POST", "/config/cache/clear"));
    }

    @Test
    void keepsConnectorAndMigrationRoutesMoreSpecificThanVendorRoutes() {
        assertEquals("connector-plugin:view",
                UserRoutePermissionPolicy.requiredPermission(
                        "GET", "/vendor/connector-migration"));
        assertEquals("connector-plugin:view",
                UserRoutePermissionPolicy.requiredPermission(
                        "GET", "/vendor/config/42/connector"));
        assertEquals("connector-plugin:view",
                UserRoutePermissionPolicy.requiredPermission(
                        "GET", "/vendor/config/42/connector-spec/catalog/generic-http/versions"));
        assertEquals("connector-plugin:view",
                UserRoutePermissionPolicy.requiredPermission(
                        "GET", "/vendor/config/connector-spec/inventory"));
        assertEquals("connector-plugin:bind",
                UserRoutePermissionPolicy.requiredPermission(
                        "PUT", "/vendor/config/42/connector-spec/draft"));
        assertEquals("connector-plugin:publish",
                UserRoutePermissionPolicy.requiredPermission(
                        "POST", "/vendor/config/42/connector/publish"));
        assertEquals("connector-plugin:migrate",
                UserRoutePermissionPolicy.requiredPermission(
                        "POST", "/vendor/connector-migration/42/prepare"));
    }

    @Test
    void representsProcessHistoryAsAnAnyOfPermissionRequirement() {
        assertEquals(java.util.List.of(
                        "api-permission:process-view", "api-permission:approve", "api-permission:view"),
                UserRoutePermissionPolicy.requiredPermissions(
                        "GET", "/api-permission/applications/42/process-history"));
        assertEquals("api-permission:process-view",
                UserRoutePermissionPolicy.requiredPermission("GET", "/api-permission/process-diagnostics"));
    }

    @Test
    void keepsSelfServiceAndCapabilityRoutesExplicit() {
        assertTrue(UserRoutePermissionPolicy.requiredPermissions(
                "GET", "/caller/apikey/current-user-options").isEmpty());
        assertEquals(java.util.List.of("api-permission:view", "api-permission:apply"),
                UserRoutePermissionPolicy.requiredPermissions("GET", "/data-test/options"));
        assertEquals(java.util.List.of("api-permission:view", "api-permission:apply"),
                UserRoutePermissionPolicy.requiredPermissions("GET", "/data-test/contract"));
        assertEquals(java.util.List.of("api-permission:view", "api-permission:apply"),
                UserRoutePermissionPolicy.requiredPermissions("POST", "/data-test/query"));
        assertTrue(UserRoutePermissionPolicy.isKnownRoute("GET", "/auth/userinfo"));
        assertTrue(UserRoutePermissionPolicy.isKnownRoute("POST", "/auth/logout"));
        assertTrue(UserRoutePermissionPolicy.isKnownRoute("GET", "/caller/apikey/current-user-options"));
        assertTrue(!UserRoutePermissionPolicy.isKnownRoute("GET", "/not-a-management-route"));
    }

    @Test
    void allowsCrossTenantBillingReadersToUseTheEventQueries() {
        assertEquals(java.util.List.of("billing:view", "billing:view-all"),
                UserRoutePermissionPolicy.requiredPermissions("GET", "/billing/event/list"));
        assertEquals(java.util.List.of("billing:view", "billing:view-all"),
                UserRoutePermissionPolicy.requiredPermissions("GET", "/billing/event/stats"));
    }

    @Test
    void exposesSceneLifecycleWithoutADeleteRoute() {
        assertEquals("call-scene:view",
                UserRoutePermissionPolicy.requiredPermission("GET", "/call-scene/list"));
        assertEquals("call-scene:add",
                UserRoutePermissionPolicy.requiredPermission("POST", "/call-scene"));
        assertEquals("call-scene:edit",
                UserRoutePermissionPolicy.requiredPermission("PUT", "/call-scene/7"));
        assertEquals("call-scene:disable",
                UserRoutePermissionPolicy.requiredPermission("PATCH", "/call-scene/7/status"));
        assertTrue(UserRoutePermissionPolicy.requiredPermissions("DELETE", "/call-scene/7").isEmpty());
    }

    @Test
    void coversNestedManagementRoutesThatDoNotMatchSingleSegmentPatterns() {
        assertEquals("role:view",
                UserRoutePermissionPolicy.requiredPermission("GET", "/role/7/permissions"));
        assertEquals("role:edit",
                UserRoutePermissionPolicy.requiredPermission("POST", "/role/7/permissions"));
        assertEquals("interface:view",
                UserRoutePermissionPolicy.requiredPermission("GET", "/interface/7/contract"));
        assertEquals("interface:view",
                UserRoutePermissionPolicy.requiredPermission("GET", "/interface/7/stats/daily"));
        assertEquals("vendor:view",
                UserRoutePermissionPolicy.requiredPermission("GET", "/vendor/code/example"));
        assertEquals("datatype:view",
                UserRoutePermissionPolicy.requiredPermission("GET", "/datatype/code/company"));
        assertEquals("security:manage",
                UserRoutePermissionPolicy.requiredPermission("POST", "/security/encryption/rotate/user_info"));
        assertEquals("interface:view",
                UserRoutePermissionPolicy.requiredPermission(
                        "GET", "/openapi-docs/interfaces/7/openapi"));
        assertEquals("trace:manage",
                UserRoutePermissionPolicy.requiredPermission("POST", "/trace/lineage"));
    }
}
