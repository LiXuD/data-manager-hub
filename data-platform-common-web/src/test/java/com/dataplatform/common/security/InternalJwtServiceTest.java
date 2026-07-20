package com.dataplatform.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.method.HandlerMethod;

class InternalJwtServiceTest {

    @TempDir
    Path tempDir;

    private InternalJwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        Path privateKey = tempDir.resolve("private.pem");
        Path publicKey = tempDir.resolve("public.pem");
        writePem(privateKey, "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        writePem(publicKey, "PUBLIC KEY", keyPair.getPublic().getEncoded());

        InternalSecurityProperties properties = new InternalSecurityProperties();
        properties.setIssuer("identity-test");
        properties.setServiceName("data-platform-masterdata");
        properties.setPrivateKeyLocation(privateKey.toUri().toString());
        properties.setPublicKeyLocation(publicKey.toUri().toString());
        jwtService = new InternalJwtService(properties, new DefaultResourceLoader());
    }

    @Test
    void issuesAndVerifiesServiceIdentity() {
        String token = jwtService.issue("data-platform-access", "data-platform-masterdata",
                Set.of("masterdata:read"));

        InternalPrincipal principal = jwtService.verify(token);

        assertEquals("data-platform-access", principal.serviceName());
        assertTrue(principal.scopes().contains("masterdata:read"));
    }

    @Test
    void rejectsTokenForAnotherAudience() {
        String token = jwtService.issue("data-platform-access", "data-platform-billing",
                Set.of("billing:calculate"));

        assertThrows(JwtValidationException.class, () -> jwtService.verify(token));
    }

    @Test
    void enforcesEndpointScope() throws Exception {
        String token = jwtService.issue("data-platform-access", "data-platform-masterdata",
                Set.of("masterdata:read"));
        InternalAuthenticationInterceptor interceptor =
                new InternalAuthenticationInterceptor(jwtService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(new SecuredHandler(),
                SecuredHandler.class.getDeclaredMethod("read"));

        assertTrue(interceptor.preHandle(request, response, handler));
        assertEquals("data-platform-access",
                ((InternalPrincipal) request.getAttribute(InternalAuthenticationInterceptor.PRINCIPAL_ATTRIBUTE))
                        .serviceName());
    }

    private void writePem(Path path, String type, byte[] bytes) throws Exception {
        String encoded = Base64.getMimeEncoder(64, System.lineSeparator().getBytes()).encodeToString(bytes);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + encoded
                + "\n-----END " + type + "-----\n");
    }

    private static class SecuredHandler {
        @InternalScope("masterdata:read")
        public void read() {
        }
    }
}
