package com.dataplatform.common.adapter;

import java.util.Map;

/**
 * 厂商适配器接口
 * 所有厂商适配器必须实现此接口
 */
public interface VendorAdapter {

    /**
     * 获取厂商编码
     */
    String getVendorCode();

    /**
     * 检查是否支持该数据类型
     */
    boolean supports(String dataTypeCode);

    /**
     * 执行数据查询
     *
     * @param config    适配器配置
     * @param params    请求参数
     * @return 响应结果
     */
    Map<String, Object> execute(VendorAdapterConfig config, Map<String, Object> params);

    /**
     * 转换请求参数 (内部字段 -> 厂商字段)
     *
     * @param params    内部参数
     * @param mapping   字段映射规则
     * @return 厂商参数
     */
    Map<String, Object> transformRequest(Map<String, Object> params, String mapping);

    /**
     * 转换响应数据 (厂商字段 -> 内部字段)
     *
     * @param response  厂商响应
     * @param mapping   字段映射规则
     * @return 标准响应
     */
    Map<String, Object> transformResponse(Map<String, Object> response, String mapping);
}
