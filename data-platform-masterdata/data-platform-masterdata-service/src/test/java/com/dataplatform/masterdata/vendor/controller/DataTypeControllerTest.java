package com.dataplatform.masterdata.vendor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.masterdata.vendor.entity.DataType;
import com.dataplatform.masterdata.vendor.mapper.DataTypeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataTypeControllerTest {

    private final DataTypeMapper dataTypeMapper = mock(DataTypeMapper.class);
    private final DataTypeController controller = new DataTypeController(dataTypeMapper);

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                DataType.class);
    }

    @Test
    void listQueriesStatusByPersistedCode() {
        when(dataTypeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    LambdaQueryWrapper<DataType> wrapper = invocation.getArgument(1);
                    assertThat(wrapper.getCustomSqlSegment()).contains("status");
                    assertThat(wrapper.getParamNameValuePairs()).containsValue("active");
                    return new Page<DataType>(1, 10);
                });

        controller.list(null, "active", 1, 10);
    }

    @Test
    void listAllQueriesActiveStatusByPersistedCode() {
        when(dataTypeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    LambdaQueryWrapper<DataType> wrapper = invocation.getArgument(0);
                    assertThat(wrapper.getCustomSqlSegment()).contains("status");
                    assertThat(wrapper.getParamNameValuePairs()).containsValue("active");
                    return List.of();
                });

        controller.listAll();
    }
}
