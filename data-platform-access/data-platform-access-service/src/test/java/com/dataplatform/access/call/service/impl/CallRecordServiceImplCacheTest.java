package com.dataplatform.access.call.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dataplatform.access.call.mapper.CallRecordMapper;
import com.dataplatform.common.entity.CallRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallRecordServiceImplCacheTest {

    private CallRecordMapper mapper;
    private CallRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                CallRecord.class);
        mapper = mock(CallRecordMapper.class);
        service = new CallRecordServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectOne(any(), eq(false))).thenReturn(null);
    }

    @Test
    void reusableCacheMustComeFromOriginalVendorCallWithinTenantAndCaller() {
        service.findLatestReusableCache(
                "PERSONAL_QUERY",
                "hash",
                7L,
                20L,
                LocalDateTime.now().minusDays(2),
                "CALLER");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<CallRecord>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectOne(wrapperCaptor.capture(), eq(false));
        String sql = wrapperCaptor.getValue().getSqlSegment();

        assertThat(sql)
                .contains("tenant_id")
                .contains("caller_id")
                .contains("cache_hit")
                .contains("cache_response_data IS NOT NULL")
                .contains("response_contract_valid IS NULL")
                .contains("OR response_contract_valid =")
                .contains("call_time");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(7L, 20L, false, true);
    }

    @Test
    void globalCacheStillCannotCrossTenantAndCannotReuseCacheHitRecords() {
        service.findLatestReusableCache(
                "PERSONAL_QUERY",
                "hash",
                7L,
                20L,
                LocalDateTime.now().minusDays(10),
                "GLOBAL");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<CallRecord>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectOne(wrapperCaptor.capture(), eq(false));
        String sql = wrapperCaptor.getValue().getSqlSegment();

        assertThat(sql).contains("tenant_id").contains("cache_hit")
                .contains("response_contract_valid IS NULL")
                .contains("OR response_contract_valid =");
        assertThat(sql).doesNotContain("caller_id");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(7L, false, true);
    }

    @Test
    void loadsTheUnmodifiedCachePayloadOnlyForReusableCache() {
        LocalDateTime callTime = LocalDateTime.now().minusMinutes(1);
        CallRecord record = new CallRecord();
        record.setId(100L);
        record.setCallTime(callTime);
        String payload = "{\"success\":true,\"data\":{\"name\":\"Alice\"}}";
        when(mapper.selectOne(any(), eq(false))).thenReturn(record);
        when(mapper.selectCacheResponseData(100L, callTime)).thenReturn(payload);

        CallRecord result = service.findLatestReusableCache(
                "PERSONAL_QUERY", "hash", 7L, 20L, LocalDateTime.now().minusDays(2), "CALLER");

        assertThat(result).isSameAs(record);
        assertThat(result.getCacheResponseData()).isEqualTo(payload);
    }

    @Test
    void excludesTheReplayPayloadFromDefaultCallRecordSelects() {
        assertThat(TableInfoHelper.getTableInfo(CallRecord.class).getAllSqlSelect())
                .doesNotContain("cache_response_data");
    }
}
