package com.dataplatform.access.approval.domain;

public enum ApplicationStatus {
    DRAFT,
    IN_REVIEW,
    PROVISIONING,
    EFFECTIVE,
    REJECTED,
    CANCELED,
    ENGINE_ERROR,
    EXPIRED,
    REVOKED
}
