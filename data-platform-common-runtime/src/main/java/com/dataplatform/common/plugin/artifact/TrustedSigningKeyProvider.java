package com.dataplatform.common.plugin.artifact;

import java.util.Optional;

@FunctionalInterface
public interface TrustedSigningKeyProvider {

    Optional<TrustedSigningKey> find(String keyId);
}
