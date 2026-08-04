package com.dataplatform.identity.security.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.common.security.InternalJwtService;
import com.dataplatform.common.security.InternalSecurityProperties;
import com.dataplatform.common.security.dto.InternalTokenRequest;
import com.dataplatform.common.security.dto.InternalTokenResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class InternalTokenControllerTest {

    private InternalJwtService jwtService;
    private InternalTokenController controller;

    @BeforeEach
    void setUp() {
        InternalSecurityProperties properties = new InternalSecurityProperties();
        properties.setTokenTtl(Duration.ofMinutes(5));
        InternalSecurityProperties.ClientRegistration accessClient =
                new InternalSecurityProperties.ClientRegistration();
        accessClient.setSecret("access-secret");
        accessClient.setGrants(Map.of(
                "data-platform-masterdata", Set.of(
                        "masterdata:read",
                        "masterdata:connector-artifact:read",
                        "masterdata:connector-runtime:read"),
                "data-platform-billing", Set.of("billing:calculate")));
        InternalSecurityProperties.ClientRegistration masterdataClient =
                new InternalSecurityProperties.ClientRegistration();
        masterdataClient.setSecret("masterdata-secret");
        masterdataClient.setGrants(Map.of(
                "data-platform-access", Set.of(
                        "access:stats:read",
                        "access:connector-runtime:read",
                        "access:connector-runtime:manage",
                        "access:connector-runtime:test")));
        properties.setClients(Map.of(
                "data-platform-access", accessClient,
                "data-platform-masterdata", masterdataClient));
        jwtService = mock(InternalJwtService.class);
        controller = new InternalTokenController(properties, jwtService);
    }

    @Test
    void issuesOnlyScopesGrantedForRequestedAudience() {
        when(jwtService.issue("data-platform-access", "data-platform-billing",
                Set.of("billing:calculate"))).thenReturn("jwt");

        InternalTokenResponse response = controller.issue(request(
                "data-platform-access", "access-secret", "data-platform-billing"));

        assertEquals("jwt", response.getAccessToken());
        assertEquals(300L, response.getExpiresIn());
        verify(jwtService).issue("data-platform-access", "data-platform-billing",
                Set.of("billing:calculate"));
    }

    @Test
    void grantsOnlyConnectorRuntimeScopesFromMasterdataToAccess() {
        Set<String> scopes = Set.of(
                "access:stats:read",
                "access:connector-runtime:read",
                "access:connector-runtime:manage",
                "access:connector-runtime:test");
        when(jwtService.issue("data-platform-masterdata", "data-platform-access", scopes))
                .thenReturn("connector-jwt");

        InternalTokenResponse response = controller.issue(request(
                "data-platform-masterdata", "masterdata-secret", "data-platform-access"));

        assertEquals("connector-jwt", response.getAccessToken());
        verify(jwtService).issue("data-platform-masterdata", "data-platform-access", scopes);
    }

    @Test
    void grantsConnectorArtifactAndRuntimeScopesFromAccessToMasterdata() {
        Set<String> scopes = Set.of(
                "masterdata:read",
                "masterdata:connector-artifact:read",
                "masterdata:connector-runtime:read");
        when(jwtService.issue("data-platform-access", "data-platform-masterdata",
                scopes)).thenReturn("artifact-jwt");

        InternalTokenResponse response = controller.issue(request(
                "data-platform-access", "access-secret", "data-platform-masterdata"));

        assertEquals("artifact-jwt", response.getAccessToken());
        verify(jwtService).issue("data-platform-access", "data-platform-masterdata",
                scopes);
    }

    @Test
    void rejectsInvalidClientSecret() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.issue(request("data-platform-access", "wrong",
                        "data-platform-billing")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void rejectsAudienceWithoutExplicitGrant() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.issue(request("data-platform-access", "access-secret",
                        "data-platform-governance")));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    private InternalTokenRequest request(String serviceName, String secret, String audience) {
        InternalTokenRequest request = new InternalTokenRequest();
        request.setServiceName(serviceName);
        request.setClientSecret(secret);
        request.setAudience(audience);
        return request;
    }
}
