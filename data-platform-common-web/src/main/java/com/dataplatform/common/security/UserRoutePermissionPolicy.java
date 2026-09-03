package com.dataplatform.common.security;

import java.util.List;
import java.util.Comparator;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

/**
 * Single, auditable permission matrix for authenticated management routes.
 *
 * <p>The matrix is deliberately kept at the HTTP boundary so a direct Gateway
 * request cannot bypass a page-level or action-level permission merely because
 * a controller returned a successful HTTP response. Object ownership remains a
 * responsibility of the domain service/controller after this check.</p>
 */
public final class UserRoutePermissionPolicy {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<RoutePermission> AUTHENTICATED_ROUTES = List.of(
            route("POST", "/auth/logout"),
            route("GET", "/auth/userinfo"),
            route("PUT", "/auth/profile"),
            route("PUT", "/auth/password"),
            route("GET", "/caller/apikey/current-user-options")
    );
    private static final List<RoutePermission> ROUTES = List.of(
            // Identity
            route("GET", "/tenant/list", "tenant:view"),
            route("GET", "/tenant/*", "tenant:view"),
            route("POST", "/tenant", "tenant:add"),
            route("PUT", "/tenant/*", "tenant:edit"),
            route("DELETE", "/tenant/*", "tenant:delete"),
            route("PATCH", "/tenant/*/status", "tenant:edit"),
            route("GET", "/user/list", "user:view"),
            route("GET", "/user/*", "user:view"),
            route("POST", "/user", "user:add"),
            route("PUT", "/user/*", "user:edit"),
            route("DELETE", "/user/*", "user:delete"),
            route("PATCH", "/user/*/status", "user:edit"),
            route("POST", "/user/*/reset-password", "user:edit"),
            route("GET", "/user/*/callers", "user:view"),
            route("POST", "/user/*/callers", "user:edit"),
            route("GET", "/user/*/roles", "user:view"),
            route("POST", "/user/*/roles", "user:edit"),
            route("GET", "/role/list", "role:view"),
            route("GET", "/role/*", "role:view"),
            route("GET", "/role/*/permissions", "role:view"),
            route("GET", "/role/*/permissionIds", "role:view"),
            route("POST", "/role/*/permissions", "role:edit"),
            route("POST", "/role", "role:add"),
            route("PUT", "/role/*", "role:edit"),
            route("DELETE", "/role/*", "role:delete"),
            route("PATCH", "/role/*/status", "role:edit"),
            route("GET", "/permission/list", "permission:view"),
            route("GET", "/permission/*", "permission:view"),
            route("GET", "/permission/all", "permission:view"),
            route("POST", "/permission", "permission:add"),
            route("PUT", "/permission/*", "permission:edit"),
            route("DELETE", "/permission/*", "permission:delete"),

            // API permission applications, approvals, grants and process diagnostics
            route("GET", "/api-permission/applications", "api-permission:view"),
            route("GET", "/api-permission/applications/*", "api-permission:view"),
            route("POST", "/api-permission/applications", "api-permission:apply"),
            route("PUT", "/api-permission/applications/*", "api-permission:apply"),
            route("POST", "/api-permission/applications/*/submit", "api-permission:apply"),
            route("POST", "/api-permission/applications/*/cancel", "api-permission:apply"),
            route("POST", "/api-permission/applications/*/copy", "api-permission:apply"),
            route("GET", "/api-permission/eligible-callers", "api-permission:apply"),
            route("GET", "/api-permission/callers/*/api-keys", "api-permission:apply"),
            route("GET", "/api-permission/interface-options", "api-permission:apply"),
            route("GET", "/api-permission/grants", "api-permission:grant-view"),
            route("POST", "/api-permission/grants/*/revoke", "api-permission:revoke"),
            route("POST", "/api-permission/emergency-grants", "api-permission:emergency-grant"),
            route("GET", "/api-permission/emergency-options/callers", "api-permission:emergency-grant"),
            route("GET", "/api-permission/emergency-options/callers/*/api-keys", "api-permission:emergency-grant"),
            route("GET", "/api-permission/emergency-options/interfaces", "api-permission:emergency-grant"),
            route("GET", "/api-permission/tasks", "api-permission:approve"),
            route("GET", "/api-permission/tasks/*", "api-permission:approve"),
            route("POST", "/api-permission/tasks/*/claim", "api-permission:approve"),
            route("POST", "/api-permission/tasks/*/unclaim", "api-permission:approve"),
            route("POST", "/api-permission/tasks/*/complete", "api-permission:approve"),
            routeAny("GET", "/api-permission/applications/*/process-history",
                    "api-permission:process-view", "api-permission:approve", "api-permission:view"),
            route("GET", "/api-permission/process-diagnostics", "api-permission:process-view"),

            // Masterdata: vendor, configuration, types and interfaces
            // Keep this exact route before /vendor/*; AntPathMatcher considers the
            // latter a match for the migration page as well.
            route("GET", "/vendor/connector-migration", "connector-plugin:view"),
            route("GET", "/vendor/connector-migration/prepared-audit", "connector-plugin:view"),
            route("POST", "/vendor/connector-migration/repair-invalid-prepared", "connector-plugin:migrate"),
            route("GET", "/vendor/list", "vendor:view"),
            route("GET", "/vendor/*", "vendor:view"),
            route("GET", "/vendor/code/*", "vendor:view"),
            route("GET", "/vendor/all", "vendor:view"),
            route("POST", "/vendor", "vendor:add"),
            route("PUT", "/vendor/*", "vendor:edit"),
            route("DELETE", "/vendor/*", "vendor:delete"),
            route("PATCH", "/vendor/*/status", "vendor:edit"),
            route("POST", "/vendor/*/test", "vendor:edit"),
            route("GET", "/vendor/config/connector-spec/*", "connector-plugin:view"),
            route("GET", "/vendor/config/*/connector-spec/catalog", "connector-plugin:view"),
            route("GET", "/vendor/config/*/connector-spec/catalog/*/versions", "connector-plugin:view"),
            route("GET", "/vendor/config/*/connector-spec/catalog/*", "connector-plugin:view"),
            route("GET", "/vendor/config/connector-spec/inventory", "connector-plugin:view"),
            route("GET", "/vendor/config/*/connector-spec/draft", "connector-plugin:view"),
            route("GET", "/vendor/config/*/connector-spec/secret-options", "connector-plugin:view"),
            route("GET", "/vendor/config/*/connector-spec/execution-plan", "connector-plugin:view"),
            route("GET", "/vendor/config/*/connector-spec/versions", "connector-plugin:view"),
            route("PUT", "/vendor/config/*/connector-spec/draft", "connector-plugin:bind"),
            route("POST", "/vendor/config/*/connector-spec/validate", "connector-plugin:bind"),
            route("POST", "/vendor/config/*/connector-spec/test", "connector-plugin:test"),
            route("POST", "/vendor/config/*/connector-spec/publish", "connector-plugin:publish"),
            route("POST", "/vendor/config/*/connector-spec/rollback/*", "connector-plugin:rollback"),
            route("POST", "/vendor/config/*/connector-spec/upgrade-preview", "connector-plugin:bind"),
            route("POST", "/vendor/config/*/connector-spec/convert-preview", "connector-plugin:bind"),
            route("POST", "/vendor/config/*/connector-spec/convert", "connector-plugin:bind"),
            route("GET", "/vendor/config/*/connector", "connector-plugin:view"),
            route("GET", "/vendor/config/*/connector/draft", "connector-plugin:view"),
            route("GET", "/vendor/config/*/connector/versions", "connector-plugin:view"),
            route("PUT", "/vendor/config/*/connector/draft", "connector-plugin:bind"),
            route("POST", "/vendor/config/*/connector/validate", "connector-plugin:bind"),
            route("POST", "/vendor/config/*/connector/test", "connector-plugin:test"),
            route("POST", "/vendor/config/*/connector/publish", "connector-plugin:publish"),
            route("POST", "/vendor/config/*/connector/rollback/*", "connector-plugin:rollback"),
            route("GET", "/vendor/config/list", "vendor:view"),
            route("GET", "/vendor/config/vendor/*", "vendor:view"),
            route("GET", "/vendor/config/interface/*", "vendor:view"),
            route("GET", "/vendor/config/security-capabilities", "vendor:view"),
            route("GET", "/vendor/config/*/security-steps", "vendor:view"),
            route("GET", "/vendor/config/*/security-versions", "vendor:view"),
            route("PUT", "/vendor/config/*/security-steps", "vendor:edit"),
            route("PUT", "/vendor/config/*/security-steps/order", "vendor:edit"),
            route("POST", "/vendor/config/*/security-preview", "vendor:edit"),
            route("POST", "/vendor/config/*/security-test", "vendor:edit"),
            route("POST", "/vendor/config/*/security-versions/*/rollback", "vendor:edit"),
            route("GET", "/vendor/config/*", "vendor:view"),
            route("POST", "/vendor/config", "vendor:add"),
            route("PUT", "/vendor/config/*", "vendor:edit"),
            route("DELETE", "/vendor/config/*", "vendor:delete"),
            route("PATCH", "/vendor/config/*/status", "vendor:edit"),
            route("POST", "/vendor/config/*/test", "vendor:edit"),
            route("GET", "/connector-plugin", "connector-plugin:view"),
            route("GET", "/connector-plugin/*", "connector-plugin:view"),
            route("GET", "/connector-plugin/*/versions", "connector-plugin:view"),
            route("POST", "/connector-plugin/versions/import", "connector-plugin:import"),
            route("POST", "/connector-plugin/*/versions/*/verify", "connector-plugin:verify"),
            route("POST", "/connector-plugin/*/versions/*/stage", "connector-plugin:activate"),
            route("GET", "/connector-plugin/*/versions/*/activation", "connector-plugin:view"),
            route("POST", "/connector-plugin/*/versions/*/activate", "connector-plugin:activate"),
            route("POST", "/connector-plugin/*/versions/*/disable", "connector-plugin:disable"),
            route("POST", "/vendor/connector-migration/*/*", "connector-plugin:migrate"),
            route("GET", "/vendor/extended-config/list", "vendor:view"),
            route("GET", "/vendor/extended-config/vendor/*", "vendor:view"),
            route("GET", "/vendor/extended-config/*", "vendor:view"),
            route("POST", "/vendor/extended-config", "vendor:add"),
            route("PUT", "/vendor/extended-config/*", "vendor:edit"),
            route("DELETE", "/vendor/extended-config/*", "vendor:delete"),
            route("PATCH", "/vendor/extended-config/*/status", "vendor:edit"),
            route("GET", "/config/list", "config:view"),
            route("GET", "/config/vendor/*", "config:view"),
            route("GET", "/config/key/*", "config:view"),
            route("GET", "/config/key/*/versions", "config:view"),
            route("GET", "/config/*", "config:view"),
            route("POST", "/config", "config:add"),
            route("POST", "/config/cache/clear", "config:edit"),
            route("POST", "/config/key/*/publish", "config:edit"),
            route("PUT", "/config/*", "config:edit"),
            route("DELETE", "/config/*", "config:delete"),
            route("PATCH", "/config/*/status", "config:edit"),
            route("GET", "/datatype/list", "datatype:view"),
            route("GET", "/datatype/*", "datatype:view"),
            route("GET", "/datatype/code/*", "datatype:view"),
            route("GET", "/datatype/all", "datatype:view"),
            route("POST", "/datatype", "datatype:add"),
            route("PUT", "/datatype/*", "datatype:edit"),
            route("DELETE", "/datatype/*", "datatype:delete"),
            route("PATCH", "/datatype/*/status", "datatype:edit"),
            route("GET", "/interface/list", "interface:view"),
            route("GET", "/interface/options", "interface:view"),
            route("GET", "/interface/by-data-type/*", "interface:view"),
            route("GET", "/interface/*/contract", "interface:view"),
            route("GET", "/interface/*/stats", "interface:view"),
            route("GET", "/interface/*/stats/daily", "interface:view"),
            route("GET", "/interface/*", "interface:view"),
            route("POST", "/interface", "interface:add"),
            route("PUT", "/interface/*/contract", "interface:edit"),
            route("PUT", "/interface/*/vendor-routing", "interface:edit"),
            route("PUT", "/interface/*", "interface:edit"),
            route("DELETE", "/interface/*", "interface:delete"),
            route("PATCH", "/interface/*/status", "interface:edit"),
            route("GET", "/graylog/list", "graylog:view"),
            route("GET", "/graylog/active/*", "graylog:view"),
            route("GET", "/graylog/*", "graylog:view"),
            route("POST", "/graylog", "graylog:add"),
            route("PUT", "/graylog/*", "graylog:edit"),
            route("DELETE", "/graylog/*", "graylog:delete"),
            route("PATCH", "/graylog/*/status", "graylog:edit"),
            // Access: callers, keys, call records and scenes
            route("GET", "/caller/list", "caller:view"),
            route("GET", "/caller/*", "caller:view"),
            route("GET", "/caller/*/products", "caller:view"),
            route("GET", "/caller/*/products/*", "caller:view"),
            route("POST", "/caller", "caller:add"),
            route("PUT", "/caller/*", "caller:edit"),
            route("DELETE", "/caller/*", "caller:delete"),
            route("PATCH", "/caller/*/status", "caller:edit"),
            route("POST", "/caller/*/products", "caller:add"),
            route("PUT", "/caller/*/products/*", "caller:edit"),
            route("GET", "/caller/apikey/list", "apikey:view"),
            route("GET", "/caller/apikey/*/interfaces", "apikey:view"),
            route("GET", "/caller/apikey/*/products", "apikey:view"),
            route("GET", "/caller/apikey/*", "apikey:view"),
            route("POST", "/caller/apikey", "apikey:add"),
            route("PUT", "/caller/apikey/*/status", "apikey:edit"),
            route("PUT", "/caller/apikey/*/rate-limit", "apikey:edit"),
            route("POST", "/caller/apikey/*/interfaces", "apikey:edit"),
            route("POST", "/caller/apikey/*/products", "apikey:edit"),
            route("DELETE", "/caller/apikey/*", "apikey:delete"),
            route("GET", "/call-record/list", "call:view"),
            route("GET", "/call-record/stats", "call:view"),
            route("GET", "/call-record/dimension-stats", "call:view"),
            route("GET", "/call-record/quality-report", "call:view"),
            route("GET", "/call-record/export", "call:export"),
            route("GET", "/call-record/*", "call:view"),
            route("POST", "/call-record/query", "call:view"),
            route("GET", "/call-scene/list", "call-scene:view"),
            route("POST", "/call-scene", "call-scene:add"),
            route("PUT", "/call-scene/*", "call-scene:edit"),
            route("PATCH", "/call-scene/*/status", "call-scene:disable"),

            // Data-test is a capability endpoint, but it still has a route-level
            // page gate so an authenticated user cannot invoke it by guessing URLs.
            routeAny("GET", "/data-test/options", "api-permission:view", "api-permission:apply"),
            routeAny("GET", "/data-test/contract", "api-permission:view", "api-permission:apply"),
            routeAny("POST", "/data-test/query", "api-permission:view", "api-permission:apply"),

            // Administrative API documentation and encryption controls
            route("GET", "/openapi-docs/interfaces/*/openapi", "interface:view"),
            route("GET", "/openapi-docs/interfaces/*", "interface:view"),
            route("POST", "/security/encryption/encrypt", "security:manage"),
            route("POST", "/security/encryption/decrypt", "security:manage"),
            route("POST", "/security/encryption/rotate/*", "security:manage"),

            // Billing
            route("GET", "/billing/plan/list", "billing:view"),
            route("GET", "/billing/plan/*", "billing:view"),
            route("GET", "/billing/template/list", "billing:view"),
            route("POST", "/billing/plan", "billing:manage"),
            route("PUT", "/billing/plan/*", "billing:manage"),
            route("DELETE", "/billing/plan/*", "billing:manage"),
            route("POST", "/billing/plan/*/validate", "billing:view"),
            route("POST", "/billing/plan/*/simulate", "billing:view"),
            route("POST", "/billing/plan/*/publish", "billing:manage"),
            route("POST", "/billing/plan/*/next-version", "billing:manage"),
            route("POST", "/billing/plan/accrue", "billing:manage"),
            route("POST", "/billing/plan/review-contracts", "billing:manage"),
            routeAny("GET", "/billing/event/list", "billing:view", "billing:view-all"),
            routeAny("GET", "/billing/event/stats", "billing:view", "billing:view-all"),
            route("POST", "/billing/event/*/reverse", "billing:reverse"),
            route("GET", "/billing/list", "billing:view"),
            route("GET", "/billing/stats", "billing:view"),
            route("GET", "/billing/export", "billing:view"),
            route("GET", "/billing/*", "billing:view"),
            route("POST", "/billing/reconciliation/import", "billing:reconcile"),
            route("POST", "/billing/reconciliation/run", "billing:reconcile"),
            route("GET", "/billing/reconciliation/list", "billing:reconcile"),
            route("GET", "/billing/reconciliation/diffs", "billing:reconcile"),

            // Governance
            route("GET", "/alert/rule/list", "monitor:view"),
            route("GET", "/alert/rule/*", "monitor:view"),
            route("POST", "/alert/rule", "monitor:manage"),
            route("PUT", "/alert/rule/*", "monitor:manage"),
            route("PATCH", "/alert/rule/*/status", "monitor:manage"),
            route("DELETE", "/alert/rule/*", "monitor:manage"),
            route("GET", "/alert/record/list", "monitor:view"),
            route("GET", "/alert/record/*", "monitor:view"),
            route("POST", "/alert/record/*/resolve", "monitor:manage"),
            route("GET", "/alert/health/list", "monitor:view"),
            route("POST", "/alert/health/*/check", "monitor:manage"),
            route("GET", "/log/list", "audit:view"),
            route("GET", "/log/stats", "audit:view"),
            route("GET", "/log/export", "audit:view"),
            route("GET", "/log/*", "audit:view"),
            route("POST", "/quality/rules", "quality:manage"),
            route("GET", "/quality/rules", "quality:view"),
            route("POST", "/quality/check", "quality:manage"),
            route("GET", "/quality/history", "quality:view"),
            route("POST", "/trace/lineage", "trace:manage"),
            route("GET", "/trace/lineage/upstream", "trace:view"),
            route("GET", "/trace/lineage/downstream", "trace:view"),
            route("GET", "/trace/lineage/full", "trace:view")
    );

    private UserRoutePermissionPolicy() {
    }

    public static String requiredPermission(String method, String path) {
        List<String> permissions = requiredPermissions(method, path);
        return permissions.isEmpty() ? null : permissions.getFirst();
    }

    public static List<String> requiredPermissions(String method, String path) {
        if (method == null || path == null) {
            return List.of();
        }
        if (matches(AUTHENTICATED_ROUTES, method, path)) {
            return List.of();
        }
        return ROUTES.stream()
                .filter(route -> route.method().matches(method)
                        && PATH_MATCHER.match(route.pattern(), path))
                .max(Comparator.comparingInt(RoutePermission::specificity))
                .map(RoutePermission::permissions)
                .orElseGet(List::of);
    }

    /**
     * Returns whether the method/path pair is explicitly known to the user-facing
     * management surface. Unknown authenticated routes are rejected by the shared
     * interceptor rather than being implicitly granted access.
     */
    public static boolean isKnownRoute(String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        return matches(ROUTES, method, path) || matches(AUTHENTICATED_ROUTES, method, path);
    }

    public static List<String> protectedRoutes() {
        return java.util.stream.Stream.concat(ROUTES.stream(), AUTHENTICATED_ROUTES.stream())
                .map(route -> route.method() + " " + route.pattern() + " -> "
                        + (route.permissions().isEmpty()
                        ? "authenticated"
                        : String.join(" | ", route.permissions())))
                .toList();
    }

    private static boolean matches(List<RoutePermission> routes, String method, String path) {
        return routes.stream().anyMatch(route -> route.method().matches(method)
                && PATH_MATCHER.match(route.pattern(), path));
    }

    private static RoutePermission route(String method, String pattern, String permission) {
        return routeAny(method, pattern, permission);
    }

    private static RoutePermission route(String method, String pattern) {
        HttpMethod.valueOf(method);
        return new RoutePermission(method, pattern, List.of());
    }

    private static RoutePermission routeAny(String method, String pattern, String... permissions) {
        HttpMethod.valueOf(method);
        return new RoutePermission(method, pattern, List.of(permissions));
    }

    private record RoutePermission(String method, String pattern, List<String> permissions) {

        private int specificity() {
            return (int) pattern.chars()
                    .filter(character -> character != '*' && character != '?')
                    .count();
        }
    }
}
