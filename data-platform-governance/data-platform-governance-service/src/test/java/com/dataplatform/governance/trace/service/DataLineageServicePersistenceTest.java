package com.dataplatform.governance.trace.service;

import com.dataplatform.governance.trace.entity.DataLineage;
import com.dataplatform.governance.trace.mapper.DataLineageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataLineageServicePersistenceTest {

    @Mock
    private DataLineageMapper dataLineageMapper;

    private DataLineageService service;

    @BeforeEach
    void setUp() {
        service = new DataLineageService();
        ReflectionTestUtils.setField(service, "baseMapper", dataLineageMapper);
    }

    @Test
    void failsWhenLineageCannotBePersisted() {
        when(dataLineageMapper.insert(any(DataLineage.class))).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.recordLineage(
                "vendor", 1L, "Vendor A",
                "interface", 2L, "Interface B",
                "PROVIDES", null));
    }
}
