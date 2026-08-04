package com.dataplatform.masterdata.connector.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

public record PluginImportRequestDTO(
        @NotBlank String artifactUri,
        @NotBlank String expectedSha256,
        @NotBlank String detachedSignature,
        @NotBlank String signingKeyId) implements Serializable {
}
