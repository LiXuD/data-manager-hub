package com.dataplatform.common.adapter;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VendorAdapterFactoryTest {

    @AfterEach
    void clearFactory() {
        VendorAdapterFactory.clearCache();
    }

    @Test
    void mockLikeVendorCodeStillUsesRealHttpAdapter() {
        assertInstanceOf(HttpVendorAdapter.class, VendorAdapterFactory.getAdapter("mock_vendor"));
    }
}
