package com.dataplatform.common.plugin.artifact;

import java.security.PublicKey;
import java.util.Objects;

public record TrustedSigningKey(String keyId, PublicKey publicKey, String signatureAlgorithm) {

    public TrustedSigningKey {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId cannot be blank");
        }
        publicKey = Objects.requireNonNull(publicKey, "publicKey");
        if (signatureAlgorithm == null || signatureAlgorithm.isBlank()) {
            throw new IllegalArgumentException("signatureAlgorithm cannot be blank");
        }
    }
}
