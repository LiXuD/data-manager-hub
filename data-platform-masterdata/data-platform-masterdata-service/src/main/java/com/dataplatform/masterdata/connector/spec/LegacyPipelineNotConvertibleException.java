package com.dataplatform.masterdata.connector.spec;

import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecConversionPreviewDTO;
import com.dataplatform.masterdata.connector.service.ConnectorConflictException;

/** Carries only the safe, structured preflight reasons for a rejected conversion. */
public final class LegacyPipelineNotConvertibleException extends ConnectorConflictException {
    private final ConnectorSpecConversionPreviewDTO preview;

    public LegacyPipelineNotConvertibleException(ConnectorSpecConversionPreviewDTO preview) {
        super("LEGACY_PIPELINE_NOT_CONVERTIBLE");
        this.preview = preview;
    }

    public ConnectorSpecConversionPreviewDTO preview() { return preview; }
}
