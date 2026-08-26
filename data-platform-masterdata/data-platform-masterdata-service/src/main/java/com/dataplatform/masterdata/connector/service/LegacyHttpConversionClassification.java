package com.dataplatform.masterdata.connector.service;

/** Migration disposition produced by the read-only Legacy HTTP preflight. */
public enum LegacyHttpConversionClassification {
    LOSSLESS_CONVERTIBLE,
    REQUIRES_DEDICATED_PLUGIN,
    MUST_REMAIN_LEGACY
}
