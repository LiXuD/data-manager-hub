package com.dataplatform.masterdata.connector.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.security.InternalScope;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorTestRequestDTO;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ConnectorControllerSecurityContractTest {

    @Test
    void internalControllersUseLeastPrivilegeScopes() {
        assertEquals("masterdata:connector-artifact:read",
                ConnectorPluginInternalController.class.getAnnotation(InternalScope.class).value());
        assertEquals("masterdata:connector-runtime:read",
                VendorConnectorInternalController.class.getAnnotation(InternalScope.class).value());
    }

    @Test
    void controlledTestNeverPersistsRequestOrResponsePayloads() throws Exception {
        Method method = VendorConnectorController.class.getMethod(
                "test", Long.class, VendorConnectorTestRequestDTO.class);
        OperationLog operationLog = method.getAnnotation(OperationLog.class);

        assertFalse(operationLog.saveParams());
        assertFalse(operationLog.saveResult());
    }
}
