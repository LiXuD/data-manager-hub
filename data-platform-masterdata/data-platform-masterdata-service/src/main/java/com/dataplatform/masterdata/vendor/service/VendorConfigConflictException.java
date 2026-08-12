package com.dataplatform.masterdata.vendor.service;

/** Conflict raised when a vendor is already bound to the same interface. */
public class VendorConfigConflictException extends RuntimeException {
    public VendorConfigConflictException(String message) {
        super(message);
    }
}
