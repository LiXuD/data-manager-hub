package com.dataplatform.masterdata.interface_.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.entity.ApiInterfaceVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ApiInterfaceService extends IService<ApiInterface> {

    PageResult<ApiInterfaceVO> list(Long vendorId, Long dataTypeId, String status, int page, int pageSize);

    List<ApiInterface> listByDataTypeId(Long dataTypeId);

    List<ApiInterface> listOptions(Long vendorId, Long dataTypeId, String status);

    ApiInterface getByInterfaceCode(String interfaceCode);

    boolean hasApiConfig(Long interfaceId);

    /**
     * 获取接口文档（请求/响应 Schema）
     */
    Map<String, Object> getInterfaceSchema(Long id);

    /**
     * 更新接口文档
     */
    boolean updateSchema(Long id, String requestSchema, String responseSchema);

    /**
     * 验证 JSON Schema 格式
     */
    boolean validateSchema(String schema);

    /**
     * 获取接口调用统计
     */
    Map<String, Object> getCallStats(Long id, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取接口每日调用统计
     */
    List<Map<String, Object>> getDailyCallStats(Long id, LocalDateTime startTime, LocalDateTime endTime);
}
