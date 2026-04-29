package com.dataplatform.call.service;

import java.util.List;
import java.util.Map;

public interface DataQueryService {

    /**
     * 单条数据查询
     * @param vendorCode 厂商编码
     * @param dataType 数据类型
     * @param interfaceCode 接口编码(可选，优先使用)
     * @param params 查询参数
     * @param callerId 调用方ID
     * @param apiKey API Key
     * @return 查询结果
     */
    Map<String, Object> queryData(String vendorCode, String dataType, String interfaceCode,
                                   Map<String, Object> params,
                                   Long callerId, String apiKey);

    /**
     * 批量数据查询
     */
    Map<String, Object> batchQuery(String vendorCode, String dataType, String interfaceCode,
                                    List<Map<String, Object>> paramsList,
                                    Long callerId, String apiKey);

    /**
     * 清除缓存
     */
    void clearCache(String vendorCode, String dataType, Map<String, Object> params);

    /**
     * 获取缓存统计
     */
    Map<String, Object> getCacheStats();
}
