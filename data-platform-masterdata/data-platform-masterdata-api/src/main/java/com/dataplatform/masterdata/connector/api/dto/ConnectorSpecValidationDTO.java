package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;

/** Safe validation result for the currently persisted SIMPLE draft. */
public record ConnectorSpecValidationDTO(
        boolean valid,
        String errorCode,
        String specHash,
        String compilerVersion,
        String compileHash,
        String compiledSnapshotHash) implements Serializable { }
