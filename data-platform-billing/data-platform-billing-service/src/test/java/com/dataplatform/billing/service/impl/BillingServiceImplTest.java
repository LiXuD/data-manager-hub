package com.dataplatform.billing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dataplatform.billing.entity.BillingDaily;
import com.dataplatform.billing.mapper.BillingDailyMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class BillingServiceImplTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void exportAppliesEveryRequestedScopeToDatabaseQuery() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                BillingDaily.class);
        BillingDailyMapper mapper = mock(BillingDailyMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        BillingServiceImpl service = new BillingServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        LocalDate startDate = LocalDate.of(2026, 7, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 21);

        byte[] csv = service.export(7L, 3L, startDate, endDate);

        ArgumentCaptor<Wrapper<BillingDaily>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<BillingDaily> wrapper = (LambdaQueryWrapper<BillingDaily>) wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        Map<String, Object> parameters = wrapper.getParamNameValuePairs();
        assertTrue(parameters.containsValue(7L));
        assertTrue(parameters.containsValue(3L));
        assertTrue(parameters.containsValue(startDate));
        assertTrue(parameters.containsValue(endDate));
        assertTrue(sqlSegment.contains("ORDER BY billing_date DESC"));
        assertEquals("\uFEFFid,tenant_id,caller_id,vendor_id,data_type,billing_date,call_count,success_count,fail_count,total_cost\n",
                new String(csv, StandardCharsets.UTF_8));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void exportNeutralizesSpreadsheetFormulasAndEscapesCsvCells() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                BillingDaily.class);
        BillingDaily row = new BillingDaily();
        row.setId(1L);
        row.setTenantId(7L);
        row.setCallerId(9L);
        row.setVendorId(3L);
        row.setDataType("=HYPERLINK(\"https://invalid\",\"x\")");
        row.setBillingDate(LocalDate.of(2026, 7, 21));
        row.setCallCount(1L);
        row.setSuccessCount(1L);
        row.setFailCount(0L);
        BillingDailyMapper mapper = mock(BillingDailyMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        BillingServiceImpl service = new BillingServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        String csv = new String(service.export(7L, null, null, null), StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"'=HYPERLINK(\"\"https://invalid\"\",\"\"x\"\")\""));
    }
}
