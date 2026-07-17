package com.dataplatform.common.security.pipeline;

public enum SecurityStepType {
    FIELD_SELECT,
    GENERATE,
    CANONICALIZE,
    DIGEST,
    HMAC,
    SIGN,
    ENCRYPT,
    DECRYPT,
    VERIFY,
    ENCODE,
    DECODE,
    INJECT,
    REMOVE_FIELD
}
