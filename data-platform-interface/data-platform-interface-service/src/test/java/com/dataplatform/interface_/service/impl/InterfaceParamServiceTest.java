package com.dataplatform.interface_.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.interface_.entity.InterfaceParam;
import com.dataplatform.interface_.mapper.InterfaceParamMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterfaceParamServiceTest {

    @Mock
    private InterfaceParamMapper interfaceParamMapper;

    @InjectMocks
    private InterfaceParamServiceImpl service;

    @Test
    void listByInterfaceIdShouldReturnSortedParams() {
        Long interfaceId = 1L;
        List<InterfaceParam> mockParams = new ArrayList<>();
        InterfaceParam param = new InterfaceParam();
        param.setParamName("name");
        param.setDescription("企业名称");
        mockParams.add(param);

        when(interfaceParamMapper.selectByInterfaceId(interfaceId)).thenReturn(mockParams);

        List<InterfaceParam> result = service.listByInterfaceId(interfaceId);

        assertEquals(1, result.size());
        assertEquals("name", result.get(0).getParamName());
        assertEquals("企业名称", result.get(0).getDescription());
        verify(interfaceParamMapper).selectByInterfaceId(interfaceId);
    }

    @Test
    void batchSaveShouldRemoveExistingThenSaveNew() {
        Long interfaceId = 1L;
        List<InterfaceParam> params = new ArrayList<>();

        InterfaceParam param1 = new InterfaceParam();
        param1.setParamName("name");
        param1.setParamType("string");
        param1.setRequired(true);
        params.add(param1);

        InterfaceParam param2 = new InterfaceParam();
        param2.setParamName("page");
        param2.setParamType("number");
        params.add(param2);

        service.batchSave(interfaceId, params);

        verify(interfaceParamMapper).delete(any(LambdaQueryWrapper.class));
        verify(interfaceParamMapper, atLeastOnce()).insert(any(), any());
        assertEquals(interfaceId, param1.getInterfaceId());
        assertEquals("string", param1.getParamType());
        assertEquals(true, param1.getRequired());
    }

    @Test
    void batchSaveShouldHandleEmptyList() {
        Long interfaceId = 1L;
        service.batchSave(interfaceId, new ArrayList<>());

        verify(interfaceParamMapper).delete(any(LambdaQueryWrapper.class));
        verify(interfaceParamMapper, never()).insert(any(), any());
    }

    @Test
    void batchSaveShouldSetDefaults() {
        Long interfaceId = 1L;
        List<InterfaceParam> params = new ArrayList<>();
        InterfaceParam param = new InterfaceParam();
        param.setParamName("test");
        params.add(param);

        service.batchSave(interfaceId, params);

        assertEquals(false, param.getRequired());
        assertEquals("string", param.getParamType());
        assertEquals(0, param.getSort());
    }

    @Test
    void getByInterfaceIdAndParamNameShouldReturnParam() {
        Long interfaceId = 1L;
        String paramName = "name";
        InterfaceParam mockParam = new InterfaceParam();
        mockParam.setParamName(paramName);

        when(interfaceParamMapper.selectByInterfaceIdAndParamName(interfaceId, paramName))
                .thenReturn(mockParam);

        InterfaceParam result = service.getByInterfaceIdAndParamName(interfaceId, paramName);

        assertNotNull(result);
        assertEquals(paramName, result.getParamName());
        verify(interfaceParamMapper).selectByInterfaceIdAndParamName(interfaceId, paramName);
    }
}
