package com.dataplatform.access.call.controller;

import com.dataplatform.access.call.api.dto.CallRecordDTO;
import com.dataplatform.access.call.api.dto.DataQueryReqDTO;
import com.dataplatform.access.call.api.dto.DataQueryRespDTO;
import com.dataplatform.access.call.api.feign.CallFeignClient;
import com.dataplatform.access.call.service.CallRecordService;
import com.dataplatform.access.call.service.DataQueryService;
import com.dataplatform.api.Result;
import com.dataplatform.common.entity.CallRecord;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/access/call")
public class CallContractController implements CallFeignClient {

    @Autowired
    private DataQueryService dataQueryService;

    @Autowired
    private CallRecordService callRecordService;

    @Override
    public Result<DataQueryRespDTO> query(DataQueryReqDTO req) {
        Map<String, Object> result = dataQueryService.queryData(
                req.getVendorCode(),
                req.getDataTypeCode() != null ? req.getDataTypeCode() : req.getDataType(),
                req.getInterfaceCode(),
                req.getParams(),
                req.getCallerId(),
                req.getApiKey());
        return Result.success(toDataQueryRespDTO(result));
    }

    @Override
    public Result<CallRecordDTO> getCallRecord(Long id) {
        return Result.success(toCallRecordDTO(callRecordService.getById(id)));
    }

    private DataQueryRespDTO toDataQueryRespDTO(Map<String, Object> result) {
        DataQueryRespDTO dto = new DataQueryRespDTO();
        dto.setRequestId(asString(result.get("requestId")));
        dto.setData(result.get("data"));
        dto.setSuccess(asBoolean(result.get("success")));
        dto.setErrorCode(asString(result.get("errorCode")));
        dto.setErrorMsg(asString(result.get("errorMsg")));
        dto.setLatency(asInteger(result.get("latency")));
        dto.setCached(asBoolean(result.get("cached")));
        return dto;
    }

    private CallRecordDTO toCallRecordDTO(CallRecord entity) {
        if (entity == null) {
            return null;
        }
        CallRecordDTO dto = new CallRecordDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Boolean asBoolean(Object value) {
        return value instanceof Boolean ? (Boolean) value : null;
    }

    private Integer asInteger(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }
}
