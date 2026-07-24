package com.dataplatform.access.approval.api;

public record InterfaceOptionResponse(
        Long id,
        String interfaceCode,
        String interfaceName,
        String status,
        boolean granted,
        boolean pending) {
}
