package com.dataplatform.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dataplatform.api.Result;
import com.dataplatform.billing.entity.BillingPlan;
import com.dataplatform.billing.mapper.BillingPlanMapper;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BillingContractReviewServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void changedResponseContractMarksPublishedPlanForReview() {
        BillingPlanMapper mapper = mock(BillingPlanMapper.class);
        ApiInterfaceFeignClient client = mock(ApiInterfaceFeignClient.class);
        BillingPlan plan = new BillingPlan();
        plan.setId(1L);
        plan.setInterfaceId(2L);
        plan.setStatus("ACTIVE");
        plan.setContractFingerprint("old-hash");
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(plan));
        InterfaceContractDTO contract = new InterfaceContractDTO();
        InterfaceParamDTO field = new InterfaceParamDTO();
        field.setParamName("newField");
        field.setParamType("string");
        contract.setResponseFields(List.of(field));
        when(client.getContract(2L)).thenReturn(Result.success(contract));
        BillingContractReviewService service = new BillingContractReviewService(
                mapper, client, new BillingConfigCodec(new ObjectMapper()));

        Map<String, Object> result = service.review();

        assertEquals(1, result.get("needsReview"));
        assertEquals("NEEDS_REVIEW", plan.getStatus());
        verify(mapper).updateById(plan);
    }
}
