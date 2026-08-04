package com.dataplatform.access.connector.runtime;

import com.dataplatform.access.connector.config.ConnectorRuntimeProperties;
import com.dataplatform.common.plugin.artifact.TrustedSigningKey;
import com.dataplatform.common.plugin.artifact.TrustedSigningKeyProvider;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

/** Loads plugin verification keys from read-only Spring resources during startup. */
public class ConfiguredTrustedSigningKeyProvider implements TrustedSigningKeyProvider {

    private final Map<String, TrustedSigningKey> keys;

    public ConfiguredTrustedSigningKeyProvider(
            ConnectorRuntimeProperties properties, ResourceLoader resourceLoader) {
        Map<String, TrustedSigningKey> loaded = new LinkedHashMap<>();
        properties.getSigningKeys().forEach((keyId, config) -> {
            if (config != null && StringUtils.hasText(config.getResource())) {
                loaded.put(keyId, load(keyId, config, resourceLoader));
            }
        });
        this.keys = Map.copyOf(loaded);
    }

    @Override
    public Optional<TrustedSigningKey> find(String signingKeyId) {
        return Optional.ofNullable(keys.get(signingKeyId));
    }

    private TrustedSigningKey load(
            String keyId, ConnectorRuntimeProperties.SigningKey config, ResourceLoader resourceLoader) {
        if (!StringUtils.hasText(keyId) || config == null || !StringUtils.hasText(config.getResource())) {
            throw new IllegalStateException("Connector signing key configuration is incomplete");
        }
        String resource = config.getResource().trim();
        if (!resource.startsWith("file:") && !resource.startsWith("classpath:")) {
            throw new IllegalStateException("Connector signing keys must use read-only file or classpath resources");
        }
        try (var input = resourceLoader.getResource(resource).getInputStream()) {
            String pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] encoded = Base64.getDecoder().decode(pem);
            String keyAlgorithm = switch (config.getAlgorithm().toUpperCase()) {
                case "ED25519" -> "Ed25519";
                case "SHA256WITHRSA", "RSA" -> "RSA";
                case "SHA256WITHECDSA", "EC" -> "EC";
                default -> throw new IllegalArgumentException("Unsupported signing algorithm");
            };
            PublicKey publicKey = KeyFactory.getInstance(keyAlgorithm)
                    .generatePublic(new X509EncodedKeySpec(encoded));
            return new TrustedSigningKey(keyId, publicKey, config.getAlgorithm());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load connector signing key: " + keyId, ex);
        }
    }
}
