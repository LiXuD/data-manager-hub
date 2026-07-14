package com.dataplatform.common.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;

public class InternalJwtService {

    private final InternalSecurityProperties properties;
    private final JwtDecoder decoder;
    private final JwtEncoder encoder;

    public InternalJwtService(InternalSecurityProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        RSAPublicKey publicKey = readPublicKey(resourceLoader, properties.getPublicKeyLocation());
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(properties.getServiceName())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
                new JwtTimestampValidator(),
                audienceValidator));
        this.decoder = jwtDecoder;

        if (hasText(properties.getPrivateKeyLocation())) {
            RSAPrivateKey privateKey = readPrivateKey(resourceLoader, properties.getPrivateKeyLocation());
            RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
            this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
        } else {
            this.encoder = null;
        }
    }

    public String issue(String subject, String audience, Set<String> scopes) {
        if (encoder == null) {
            throw new IllegalStateException("Internal JWT private key is not configured");
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plus(properties.getTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("scope", String.join(" ", scopes))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public InternalPrincipal verify(String token) {
        Jwt jwt = decoder.decode(token);
        String scope = jwt.getClaimAsString("scope");
        Set<String> scopes = scope == null || scope.isBlank()
                ? Set.of()
                : Set.of(scope.split("\\s+"));
        return new InternalPrincipal(jwt.getSubject(), properties.getServiceName(), scopes);
    }

    private static RSAPublicKey readPublicKey(ResourceLoader loader, String location) {
        try {
            byte[] bytes = readPem(loader, location, "PUBLIC KEY");
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load internal JWT public key", e);
        }
    }

    private static RSAPrivateKey readPrivateKey(ResourceLoader loader, String location) {
        try {
            byte[] bytes = readPem(loader, location, "PRIVATE KEY");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load internal JWT private key", e);
        }
    }

    private static byte[] readPem(ResourceLoader loader, String location, String type) throws Exception {
        if (!hasText(location)) {
            throw new IllegalArgumentException(type + " location is required");
        }
        try (InputStream input = loader.getResource(location).getInputStream()) {
            String pem = new String(input.readAllBytes());
            String normalized = pem.replace("-----BEGIN " + type + "-----", "")
                    .replace("-----END " + type + "-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(normalized);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
