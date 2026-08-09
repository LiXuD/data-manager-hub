package com.dataplatform.masterdata.connector.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.masterdata.connector.mapper.VendorConnectorMigrationMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class VendorConnectorMigrationServiceImplTest {

    @Test
    void exposesMigrationFactsAsReadOnlyHistory() {
        VendorConnectorMigrationMapper mapper = mock(VendorConnectorMigrationMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        VendorConnectorMigrationServiceImpl service = new VendorConnectorMigrationServiceImpl(mapper);

        assertThat(service.list("stable")).isEmpty();
        verify(mapper).selectList(any());
    }
}
