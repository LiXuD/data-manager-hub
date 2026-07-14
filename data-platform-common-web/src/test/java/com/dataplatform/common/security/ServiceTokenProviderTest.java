package com.dataplatform.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class ServiceTokenProviderTest {

    private InternalSecurityProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new InternalSecurityProperties();
        properties.setServiceName("data-platform-access");
        properties.setClientSecret("access-secret");
        properties.setTokenUri("http://identity/internal-auth/v1/token");
        properties.setTokenMaxAttempts(2);
        properties.setTokenRetryBackoff(Duration.ZERO);
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    @Test
    void cachesTokenUntilRefreshWindow() {
        server.expect(once(), requestTo(properties.getTokenUri()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"accessToken\":\"token-1\",\"expiresIn\":300}",
                        MediaType.APPLICATION_JSON));
        ServiceTokenProvider provider = new ServiceTokenProvider(properties, restClientBuilder);

        assertEquals("token-1", provider.getToken("data-platform-masterdata"));
        assertEquals("token-1", provider.getToken("data-platform-masterdata"));
        server.verify();
    }

    @Test
    void retriesServerFailureThenSucceeds() {
        server.expect(once(), requestTo(properties.getTokenUri())).andRespond(withServerError());
        server.expect(once(), requestTo(properties.getTokenUri()))
                .andRespond(withSuccess("{\"accessToken\":\"token-2\",\"expiresIn\":300}",
                        MediaType.APPLICATION_JSON));
        ServiceTokenProvider provider = new ServiceTokenProvider(properties, restClientBuilder);

        assertEquals("token-2", provider.getToken("data-platform-billing"));
        server.verify();
    }

    @Test
    void doesNotRetryInvalidCredentials() {
        server.expect(once(), requestTo(properties.getTokenUri()))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        ServiceTokenProvider provider = new ServiceTokenProvider(properties, restClientBuilder);

        assertThrows(RestClientResponseException.class,
                () -> provider.getToken("data-platform-billing"));
        server.verify();
    }
}
