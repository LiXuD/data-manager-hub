package com.dataplatform.masterdata.interface_.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.masterdata.interface_.entity.InterfaceParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class InterfaceParamServiceImplPersistenceTest {

    private InterfaceParamServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new InterfaceParamServiceImpl());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mock(BaseMapper.class));
    }

    @Test
    void failsWhenBatchInsertCannotPersistParameters() {
        doReturn(false).when(service).saveBatch(anyList());

        assertThrows(IllegalStateException.class,
                () -> service.batchSave(1L, List.of(new InterfaceParam())));
    }

    @Test
    void rejectsNullInterfaceIdBeforeChangingParameters() {
        assertThrows(IllegalArgumentException.class, () -> service.batchSave(null, List.of()));
    }
}
