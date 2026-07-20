package com.dataplatform.identity.security.controller;

import com.dataplatform.common.security.InternalJwtService;
import com.dataplatform.common.security.InternalSecurityProperties;
import com.dataplatform.common.security.dto.InternalTokenRequest;
import com.dataplatform.common.security.dto.InternalTokenResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal-auth/v1")
@ConditionalOnProperty(prefix = "platform.security.internal", name = "enabled", havingValue = "true")
public class InternalTokenController {

    private final InternalSecurityProperties properties;
    private final InternalJwtService jwtService;

    public InternalTokenController(InternalSecurityProperties properties, InternalJwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    @PostMapping("/token")
    public InternalTokenResponse issue(@RequestBody InternalTokenRequest request) {
        InternalSecurityProperties.ClientRegistration client =
                properties.getClients().get(request.getServiceName());
        if (client == null || !secretMatches(client.getSecret(), request.getClientSecret())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service credentials");
        }
        Set<String> scopes = client.getGrants().get(request.getAudience());
        if (scopes == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Audience is not allowed");
        }

        InternalTokenResponse response = new InternalTokenResponse();
        response.setAccessToken(jwtService.issue(request.getServiceName(), request.getAudience(), scopes));
        response.setExpiresIn(properties.getTokenTtl().toSeconds());
        return response;
    }

    private boolean secretMatches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
